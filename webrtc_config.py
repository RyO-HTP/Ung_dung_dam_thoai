import json

def get_ice_servers_config():
    # Cấu hình danh sách máy chủ STUN và TURN thực tế từ Metered
    ice_servers = {
        "iceServers": [
            {
                "urls": "stun:stun.relay.metered.ca:80"
            },
            {
                "urls": "turn:global.relay.metered.ca:80",
                "username": "a45884b7286d688684c2c4cc",
                "credential": "4e600MW8eaVVsPNJ"
            },
            {
                "urls": "turn:global.relay.metered.ca:80?transport=tcp",
                "username": "a45884b7286d688684c2c4cc",
                "credential": "4e600MW8eaVVsPNJ"
            },
            {
                "urls": "turn:global.relay.metered.ca:443",
                "username": "a45884b7286d688684c2c4cc",
                "credential": "4e600MW8eaVVsPNJ"
            },
            {
                "urls": "turns:global.relay.metered.ca:443?transport=tcp",
                "username": "a45884b7286d688684c2c4cc",
                "credential": "4e600MW8eaVVsPNJ"
            }
        ]
    }
    return json.dumps(ice_servers)

if __name__ == "__main__":
    print("Cấu hình ICE Servers ban đầu:")
    print(get_ice_servers_config())