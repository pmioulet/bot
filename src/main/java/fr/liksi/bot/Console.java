package fr.liksi.bot;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;

/**
 * Diaglogflow console
 *
 */
public class Console {

    private SessionsClient client;

    private String project;

    public Console(String credentialFile, String project) throws FileNotFoundException, IOException {
        this.project = project;
        Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialFile));
        SessionsSettings settings = SessionsSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        client = SessionsClient.create(settings);
    }

    public String request(String sessionId, String message) {
        QueryInput queryInput;
        queryInput = QueryInput.newBuilder()
                .setText(
                        TextInput.newBuilder()
                        .setText(message)
                        .setLanguageCode("EN")
                        .build())
                .build();

        // Perform query
        SessionName session = SessionName.of(project, sessionId);
        DetectIntentResponse actualResponse = client.detectIntent(session, queryInput);
        return actualResponse.getQueryResult().getFulfillmentText();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("You must pass 2 arguments <CREDENTIAL_FILE> <PROJECT>");
            System.exit(-1);
        }
        Console client = new Console(args[0], args[1]);

        String sessionId = UUID.randomUUID().toString();

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Talk to the bot, press Ctrl-D to exit\n");
        System.out.print("Me: ");
        String userInput = br.readLine();
        while (userInput != null) {
            System.out.print("Bot: ");
            System.out.println(client.request(sessionId, userInput));
            System.out.print("Me: ");
            userInput = br.readLine();
        }
    }
}
