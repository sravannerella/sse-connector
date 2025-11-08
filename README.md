# SSE Connector

The SSE Connector enables Server-Sent Events (SSE) support in Mule applications, allowing for real-time event streaming from the server to connected clients.


## Requirements
- Java 17
- Mule runtime version 4.9.0 or higher
- Maven 3.9.6

## Installation

```bash
mvn clean install -U
```

Add this dependency to your application pom.xml
```xml
    <dependency>
        <groupId>com.k2</groupId>
        <artifactId>sse-connector</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <classifier>mule-plugin</classifier>
    </dependency>
```


## Deploy to Anypoint Exchange
To deploy the connector to your Anypoint Exchange, use the following Maven command with Anypoint Credentials configured:

```bash
mvn deploy
```

Here is the [MuleSoft official Documentation](https://docs.mulesoft.com/exchange/to-publish-assets-maven) for more details on deploying to Anypoint Exchange.

## Example Flow
Here is an example Mule flow that demonstrates how to use the SSE Connector to stream events to clients:

![SSE Flow](images/image.png)

## Contributions
Contributions are welcome! Please fork the repository and submit a pull request with your changes.