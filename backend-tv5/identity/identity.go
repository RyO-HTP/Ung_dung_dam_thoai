package identity

type Identity struct {
	UserID       string
	NumericID    uint32
	EventID      int64
	DisplayName  string
	RoleTitle    string
	UserLevel    uint8
	RolePriority uint8
}

type Resolver interface {
	Resolve(ref string) (Identity, bool)
}
