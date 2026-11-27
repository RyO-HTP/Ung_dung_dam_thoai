package week4floor

import (
	"encoding/json"
	"net/http"
	"strconv"
	"sync"
	"time"

	"damthoai/nguoi5server/identity"
	"github.com/gorilla/websocket"
)

type FloorSubscriber struct {
	UserID   string
	EventID  int64
	Conn     *websocket.Conn
	SendChan chan []byte
}

type FloorHub struct {
	mu          sync.RWMutex
	engine      *FloorControlEngine
	subscribers map[string]*FloorSubscriber
	upgrader    websocket.Upgrader
}

func NewFloorHub(args ...interface{}) *FloorHub {
	var engine *FloorControlEngine
	for _, arg := range args {
		if e, ok := arg.(*FloorControlEngine); ok {
			engine = e
		}
	}
	if engine == nil {
		engine = NewFloorControlEngine(nil)
	}

	hub := &FloorHub{
		engine:      engine,
		subscribers: make(map[string]*FloorSubscriber),
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
	}

	existingCb := engine.onFloorEvent
	engine.onFloorEvent = func(decision FloorDecision) {
		if existingCb != nil {
			existingCb(decision)
		}
		hub.BroadcastDecision(decision)
	}

	return hub
}

func (h *FloorHub) BroadcastDecision(decision FloorDecision) {
	var eventID int64
	h.engine.mu.RLock()
	resolver := h.engine.identityResolver
	h.engine.mu.RUnlock()

	var _ identity.Resolver = resolver

	if resolver != nil && decision.ActiveHolder != nil {
		if id, ok := resolver.Resolve(decision.ActiveHolder.UserID); ok {
			eventID = id.EventID
		}
	}

	payload := map[string]interface{}{
		"type":               "FLOOR_DECISION",
		"eventId":            eventID,
		"status":             decision.Status,
		"channelId":          decision.ChannelID,
		"channelCode":        decision.ChannelCode,
		"requesterCallState": decision.RequesterCallState,
		"listenerCallState":  decision.ListenerCallState,
		"revokedCallState":   decision.RevokedCallState,
		"isDucked80":         decision.IsDucked80,
		"duckingDb":          decision.DuckingDb,
		"activeHolder":       decision.ActiveHolder,
		"revokedUserId":      decision.RevokedUserID,
		"revokedDisplayName": decision.RevokedDisplayName,
		"reason":             decision.Reason,
		"timestampMs":        decision.TimestampMs,
	}

	raw, err := json.Marshal(payload)
	if err != nil {
		return
	}

	h.mu.RLock()
	defer h.mu.RUnlock()
	for _, sub := range h.subscribers {
		if eventID == 0 || sub.EventID == 0 || sub.EventID == eventID {
			select {
			case sub.SendChan <- raw:
			default:
			}
		}
	}
}

func (h *FloorHub) ServeFloorWS(w http.ResponseWriter, r *http.Request) {
	userId := r.URL.Query().Get("userId")
	eventIdStr := r.URL.Query().Get("eventId")
	var eventID int64
	if eventIdStr != "" {
		eventID, _ = strconv.ParseInt(eventIdStr, 10, 64)
	}

	if userId == "" {
		userId = "anon-" + time.Now().Format("150405.000")
	}

	h.engine.mu.RLock()
	resolver := h.engine.identityResolver
	h.engine.mu.RUnlock()

	var _ identity.Resolver = resolver

	if resolver != nil {
		id, ok := resolver.Resolve(userId)
		if !ok {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "USER_NOT_IN_EVENT",
				"message": "User not found in event",
			})
			return
		}
		if eventID != 0 && id.EventID != eventID {
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusForbidden)
			_ = json.NewEncoder(w).Encode(map[string]string{
				"error":   "USER_NOT_IN_EVENT",
				"message": "User does not belong to event",
			})
			return
		}
		if eventID == 0 {
			eventID = id.EventID
		}
	}

	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}

	subID := userId + "-" + time.Now().Format("150405.000.nanos")

	sub := &FloorSubscriber{
		UserID:   userId,
		EventID:  eventID,
		Conn:     conn,
		SendChan: make(chan []byte, 64),
	}

	h.mu.Lock()
	h.subscribers[subID] = sub

	holders := h.engine.Snapshot()
	var eventHolders []FloorHolder
	for _, hld := range holders {
		if eventID == 0 || resolver == nil {
			eventHolders = append(eventHolders, hld)
		} else {
			if id, ok := resolver.Resolve(hld.UserID); ok && id.EventID == eventID {
				eventHolders = append(eventHolders, hld)
			}
		}
	}

	snapshotPayload := map[string]interface{}{
		"type":          "FLOOR_SNAPSHOT",
		"eventId":       eventID,
		"holders":       eventHolders,
		"activeHolders": eventHolders,
		"serverTimeMs":  time.Now().UnixMilli(),
	}
	h.mu.Unlock()

	if raw, err := json.Marshal(snapshotPayload); err == nil {
		sub.SendChan <- raw
	}

	defer func() {
		h.mu.Lock()
		delete(h.subscribers, subID)
		close(sub.SendChan)
		h.mu.Unlock()
		_ = conn.Close()
	}()

	go func() {
		for msg := range sub.SendChan {
			_ = conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		}
	}()

	for {
		_, _, err := conn.ReadMessage()
		if err != nil {
			break
		}
	}
}
