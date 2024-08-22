package org.example;

import io.confluent.connect.hub.actions.ConfluentHubController;
import io.confluent.connect.hub.cli.ConfluentHubClient;
import io.confluent.connect.hub.cli.interaction.AutoPilotInstall;
import io.confluent.connect.hub.io.ConfluentHubStorage;
import io.confluent.connect.hub.io.Storage;
import io.confluent.connect.hub.platform.PlatformInspector;
import io.confluent.connect.hub.rest.PluginRegistryRepository;
import io.confluent.connect.hub.rest.Repository;
import io.confluent.connect.hub.utils.IOUtils;
import io.confluent.connect.hub.utils.NamingUtils;
import io.confluent.pluginregistry.rest.entities.PluginManifest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Collections;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class HubImportToNexus {
    private static final Logger logger = LogManager.getLogger(HubImportToNexus.class);
    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Usage: java HubTest <connector-name> <version> <destination-temp-dir> <nexus-url> <nexus-repo> <nexus-username> <nexus-passwd>");
            return;
        }

        String connectorName = args[0];
        String version = args[1];
        String destinationDir = args[2];
        String nexusUrl = args[3];
        String nexusRepo = args[4];
        String nexusUsername = args[5];
        String nexusPassword = args[6];

        Storage confluentHubStorage = new ConfluentHubStorage();
        Repository repository = new PluginRegistryRepository("https://api.hub.confluent.io");
        ConfluentHubController controller = new ConfluentHubController(confluentHubStorage, repository);
        PlatformInspector platformInspector = new PlatformInspector(confluentHubStorage, Runtime.getRuntime());

        AutoPilotInstall api = new AutoPilotInstall(connectorName + ":" + version, destinationDir, Collections.emptyList(), platformInspector);
        PluginManifest manifest = repository.getManifest(NamingUtils.getPathForComponent(NamingUtils.parsePluginId(api.getComponent())));

        IOUtils.info("Downloading component {} {}, provided by {} from Confluent Hub and installing into ", new Object[]{manifest.getTitle(), manifest.getVersion(), manifest.getOwner().getName()});

        try {
            // Get the URI of the archive and download it
            URI archiveUri = NamingUtils.getArchiveUri(manifest.getArchive());
            File downloadedFile = new File(destinationDir, manifest.getName()+".zip");
            downloadFile(archiveUri.toURL(), downloadedFile);

            System.out.println("Connector downloaded successfully to " + destinationDir);

            // Upload to Nexus
            String versionedNexusUrl = nexusUrl + nexusRepo + "/" + connectorName + "/" + version + "/" + downloadedFile.getName();
            uploadToNexus(downloadedFile, versionedNexusUrl, nexusUsername, nexusPassword);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to download or upload the connector.");
        }
    }

    private static void downloadFile(URL url, File destination) throws Exception {
        URLConnection connection = url.openConnection();
        InputStream inputStream = connection.getInputStream();

        try (FileOutputStream outputStream = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        inputStream.close();
    }

    private static void uploadToNexus(File file, String nexusUrl, String username, String password) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPut uploadFile = new HttpPut(nexusUrl);
            FileEntity fileEntity = new FileEntity(file);
            uploadFile.setEntity(fileEntity);

            // Encode credentials in Base64
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            uploadFile.setHeader("Authorization", "Basic " + encodedAuth);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200 || statusCode == 201) {
                    System.out.println("File uploaded successfully to Nexus.");
                } else {
                    System.out.println("Failed to upload file to Nexus. HTTP Status Code: " + statusCode);
                }
            }
        }
    }
}
