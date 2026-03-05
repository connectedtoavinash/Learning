#!/bin/bash

for i in $(seq 1 6); do
PORT=$((7000 + $i))
BUS_PORT=$((17000 + $i))

cat > redis-node-${i}.conf <<EOF
port ${PORT}
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
cluster-announce-ip 173.18.0.$((i + 1))
cluster-announce-port ${PORT}
cluster-announce-bus-port ${BUS_PORT}
protected-mode no
bind 0.0.0.0
EOF

echo "✅ redis-node-${i}.conf created (Port: ${PORT})"
done