#!/bin/bash

# Check if Nexus container is already running
if [ "$(docker ps -q -f name=nexus)" ]; then
    echo "Nexus container is already running."
else
    echo "Starting Nexus container..."
    docker run -d -p 8081:8081 --name nexus sonatype/nexus3
    # Wait for Nexus to be fully up and running
    echo "Waiting for Nexus to start..."
    until curl -s -o /dev/null -w "%{http_code}" http://localhost:8081 | grep -q "200"; do
      sleep 10
    done
    echo "Nexus is up and running."
fi

# Define the Nexus URL, the destination directory, and the repository
NEXUS_URL="http://localhost:8081/repository/"
NEXUS_REPO="kafka-plugins"
DEST_DIR="/tmp/connectors"
NEXUS_USER="admin"
NEXUS_PASSWORD="admin123"

if [ ! -d "$DEST_DIR" ]; then
    echo "Destination directory $DEST_DIR does not exist. Creating it..."
    mkdir -p "$DEST_DIR"
    echo "Destination directory $DEST_DIR created."
fi
# Define the connectors and their versions to upload
declare -A CONNECTORS
CONNECTORS["confluentinc/kafka-connect-azure-blob-storage"]="latest"
CONNECTORS["confluentinc/kafka-connect-jdbc"]="latest"
CONNECTORS["confluentinc/kafka-connect-datagen"]="latest"

# Loop through each connector and call the Java program to upload it
for CONNECTOR in "${!CONNECTORS[@]}"; do
    VERSION="${CONNECTORS[$CONNECTOR]}"
    echo "Uploading $CONNECTOR:$VERSION to Nexus..."
    java -jar ./build/libs/hub-importer-nexus-1.0.0-all.jar $CONNECTOR $VERSION $DEST_DIR $NEXUS_URL $NEXUS_REPO $NEXUS_USER $NEXUS_PASSWORD
    if [ $? -eq 0 ]; then
        echo "$CONNECTOR:$VERSION uploaded successfully."
    else
        echo "Failed to upload $CONNECTOR:$VERSION."
    fi
done

echo "All connectors processed."