# Dialogflow & Docker

## Introduction

Chez Liksi, on aime bien les chatbots. Et on aime bien Docker. Du coup, on aime bien les chatbots dans Docker. Il se trouve que l'intégration de la dernière version en date de [Dialogflow](https://dialogflow.com/) (0.64.0-alpha) nous a posé quelques soucis et on s'est dit qu'on vous ferait partager leurs résolutions.

Pour ceux qui l'ignoreraient, Dialogflow (autrefois api.ai) est un framework Natural Language Processing de Google (NLP). En clair, il peut vous aider à reconnaître le sens d'un message qu'un utilisateur écrit dans un langage non informatique, soit la langue de Molière par exemple.

Dans ce billet, nous allons intégrer Dialogflow en utilisant l'api Java. Le cas d'utilisation est une intégration à la console pour discuter avec le bot depuis son shell par exemple. Nous avons en réalité rencontré ces problèmes lors d'une intégration à un projet Spring Boot mais pour simplifier nous avons laissé Spring Boot de côté.

## Création d'un agent préconfiguré sur Dialogflow

Première étape, on créé un bot simple sur Dialogflow. A l'aide de son compte google on se connecte [ici](https://console.dialogflow.com/api-client/#/login) en autorisant les accès que Dialogflow réclame sur son compte.

Ensuite, on instancie un agent. Un intent permet à Dialogflow d'associer une phrase en langage naturel à une intention. La création des intents n'est pas le but de ce billet, nous allons sauter cette étape en utilisant une série d'intents fournie par Google. Cela nous permettra de converser avec notre bot.
![Agent-Creation](src/main/img/02-Agent-smalltalk.gif?raw=true "Agent-Creation")

On teste ce petit agent préconfiguré dans l'interface:
![Agent-Test](src/main/img/03-Agent-test.gif?raw=true "Agent-Test")

## Création d'un projet java

Maintenant que l'on a notre bot, on va tenter de l'intégrer au sein d'une application Java.

On va utiliser un projet Maven:

``` bash
mvn archetype:generate -DgroupId=fr.liksi.bot -DartifactId=bot -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

On ajoute la dépendance à la librairie client au pom.xml:

``` xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>fr.liksi.bot</groupId>
  <artifactId>bot</artifactId>
  <version>1.0-SNAPSHOT</version>
  <properties>
    <dialogflow.version>0.46.0-alpha</dialogflow.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.google.cloud</groupId>
      <artifactId>google-cloud-dialogflow</artifactId>
      <version>${dialogflow.version}</version>
    </dependency>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.12</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <finalName>${project.artifactId}</finalName>
  </build>
</project>
```

Maintenant, on implémente notre console. L'objet [SessionsClient](http://googlecloudplatform.github.io/google-cloud-java/0.46.0/apidocs/com/google/cloud/dialogflow/v2beta1/SessionsClient.html) est celui qui permet d'échanger des messages avec Dialogflow.

On créé une instance dans le constructeur:

``` java
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
}
```

Le premier paramètre permet de s'authentifier à l'api google on reviendra sur son obtention plus bas, le second correspond au nom du projet.

Maintenant on implémente la fonction qui permet d'envoyer un message au serveur:

``` java
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
```

Le paramètre sessionId est un identifiant unique de session généré côté client. Il permet à Dialogflow d'assurer le suivi des conversations.

Enfin le main qui nous permet d'intéragir avec l'entrée utilisateur:

``` java
    public static void main(String[] args) throws IOException {
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
```

## Récupération de la clé d'api Dialogflow v2

L'authentification se faisait avec un simple header http pour la v1. Pour la v2, il faut utiliser l'api Google cloud, c'est un petit peu plus complexe. Cette phase va nous permettre de récupérer un fichier json qui sera utilisé par la console.

Les étapes pour récupérer ce fichier sont expliquées [ici](https://dialogflow.com/docs/reference/v2-auth-setup).

## Création de l'uber-jar

Il existe plusieurs méthode pour créer un uber-jar qui contient toutes les dépendances d'un projet. En voici une utilisant le shade plugin de maven:

``` xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>2.4.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer
                        implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>fr.liksi.bot.Console</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

On peut maintenant communiquer avec Dialogflow depuis la ligne de commande avec la commande suivante:

``` bash
java -jar target/bot.jar <AUTH_FILE> <PROJECT_ID>
```

## Dockerisation

Jusqu'ici tout roule et du coup on se dit qu'on va packager tout ce projet dans un conteneur Docker. On base le conteneur sur alpine pour obtenir un conteneur de faible taille.

### Episode 1

Et hop notre premier Dockerfile devient:

``` bash
FROM openjdk:8-jdk-alpine
RUN adduser -D -h /home/bot -s /bin/sh bot
USER bot

COPY target/bot.jar /bot.jar

ENTRYPOINT [ "java", "-jar", "/bot.jar" ]
```

On build et on run avec le volume qui va bien:

``` bash
Docker build -t liksi/bot .
Docker run -v /config/A04850DIRautobot-V2-7e03636307f2.json:/config/A04850DIRautobot-V2-7e03636307f2.json liksi/bot /config/A04850DIRautobot-V2-7e03636307f2.json a04850dirautobot-v2
```

Et là... c'est le drame:

``` java
Exception in thread "main" java.lang.IllegalArgumentException: ALPN is not configured properly. See https://github.com/grpc/grpc-java/blob/master/SECURITY.md#troubleshooting for more information.
  at io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.selectApplicationProtocolConfig(GrpcSslContexts.java:166)
  at io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.configure(GrpcSslContexts.java:136)
  at io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.configure(GrpcSslContexts.java:124)
  at io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts.forClient(GrpcSslContexts.java:94)
  ...
```

Heureusement, Google nous donne un [lien très informatif](https://github.com/grpc/grpc-java/blob/master/SECURITY.md#troubleshooting) dans la stack. Ce lien nous apprend que alpine n'est pas supporté par la librairie netty-tcnative utilisé pour la couche de transport sécurisée. Il nous indique aussi qu'il est possible d'utiliser [netty-tcnative-alpine](https://github.com/pires/netty-tcnative-alpine) à la place.

### Episode 2

Grâce au Dockerfile de [netty-tcnative-alpine](https://github.com/pires/netty-tcnative-alpine), on réalise qu'il faut recompiler la librarie pour alpine. Soit, c'est le moment d'utiliser une feature récente de Docker, le [multi stage build](https://docs.Docker.com/develop/develop-images/multistage-build/). Pour faire court, cela va nous permettre d'utiliser un conteneur Docker avec tous les binaires de compilation dont on a besoin pour générer la librairie requise pour le projet final. Tout ceci en évitant que les dépendances seulement nécessaires à la compilation ne se retrouvent dans le conteneur final.

Et en s'inspirant un peu du projet ci-dessus on arrive à ceci:

``` bash
#############
# Build tcnative-alpine
#############
FROM openjdk:8-jdk-alpine AS tcnative-builder

RUN apk add --update \
        linux-headers build-base autoconf automake libtool apr-util apr-util-dev git cmake ninja go

ENV NETTY_TCNATIVE_TAG netty-tcnative-parent-2.0.8.Final
ENV MAVEN_VERSION 3.3.9
ENV MAVEN_HOME /usr/share/maven

# Install mvn
WORKDIR /usr/share
RUN wget http://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz -O - | tar xzf -
RUN mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

# Build tcnative
RUN git clone https://github.com/netty/netty-tcnative
WORKDIR netty-tcnative
RUN git checkout tags/$NETTY_TCNATIVE_TAG
RUN mvn clean package

################
# Final Dockerfile
################
FROM openjdk:8-jdk-alpine
RUN apk add --update libuuid
RUN adduser -D -h /home/bot -s /bin/sh bot
USER bot

COPY target/bot.jar /bot.jar
COPY --from=tcnative-builder /usr/share/netty-tcnative/boringssl-static/target/netty-tcnative-boringssl-static-*-linux-x86_64.jar /libs/

ENTRYPOINT [ "java", "-cp", "/lib-boring-ssl/", "-jar", "/bot.jar" ]
```

**NB: Pour la version de Dialogflow 0.46-alpha utilisée ici, Google recommande la version 2.0.7.Final, mais cette version ne compile pas sur Alpine pour cause d'une dépendance non résolue...**

Et là on build, on attend pas mal de temps... Et au final on arrive à la même exception que précédemment...

### Episode 3

Après un peu de réflexion, on se dit qu'on a ajouté la librairie qui va bien dans le classpath mais que ça serait aussi une bonne idée de retirer celle qui ne va pas.

Pour garder un projet qui tourne à la fois localement et sur le conteneur, on va compiler notre projet dans notre Dockerfile tout en récupérant les dépendances nécessaires.

Dans le pom on ajoute ceci:

``` xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>copy-dependencies</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>copy-dependencies</goal>
            </goals>
            <configuration>
                <outputDirectory>
                    ${project.build.directory}/libs
                </outputDirectory>
                <excludeArtifactIds>
                    grpc-netty-shaded
                </excludeArtifactIds>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Et on ajoute une tâche de compilation et la copie des artefacts dans notre Dockerfile:

``` bash
COPY --from=tcnative-builder /bot/target/libs /libs
COPY --from=tcnative-builder /bot/target/original-bot.jar /libs/
COPY --from=tcnative-builder /usr/share/netty-tcnative/boringssl-static/target/netty-tcnative-boringssl-static-*-linux-x86_64.jar /libs/

ENTRYPOINT [ "java", "-cp", "/libs/*", "fr.liksi.bot.Console" ]
```

Et on lance notre conteneur ainsi:

``` bash
docker run -v credentials.json:/config/credentials.json liksi/bot /config/credentials.json <PROJECT_ID>
```

![Docker-Test](src/main/img/04-Docker-test.gif?raw=true "Docker-Test")