docker run \
    --name avahi \
    --network=host \
    --hostname=umbrela-bridge-TONG \
    -d \
    --restart=always \
    umbrela/avahi:0.6.32-arm64
