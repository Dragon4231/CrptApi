import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private final Lock lock = new ReentrantLock();
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private int requestCount;
    private long lastResetTime;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final String token;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String token) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.token = token;
        this.requestCount = 0;
        this.lastResetTime = System.currentTimeMillis();
    }

    public void createDocument(String productDocument, String documentFormat,
                               String productGroup, String signature, String type) throws IOException {
        checkRequestLimit();
        try {
            lock.lock();

            String encodedProductDocument = Base64.getEncoder().encodeToString(productDocument.getBytes(StandardCharsets.UTF_8));

            String requestBody = String.format("{ " +
                    "\"document_format\":\"%s\"," +
                    "\"product_document\":\"%s\"," +
                    "\"product_group\":\"%s\"," +
                    "\"signature\":\"%s\"," +
                    "\"type\":\"%s\"" +
                    "}", documentFormat, encodedProductDocument, productGroup, signature, type);

            URL url = new URL(apiUrl + "?pg=" + productGroup);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);
            }
            requestCount++;
            lastResetTime = System.currentTimeMillis();
        } finally {
            lock.unlock();
        }
    }

    private void checkRequestLimit() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastResetTime;

        if (elapsed > timeUnit.toMillis(1)) {
            requestCount = 0;
            lastResetTime = currentTime;
        }

        while (requestCount >= requestLimit) {
            try {
                Thread.sleep(timeUnit.toMillis(1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            elapsed = System.currentTimeMillis() - lastResetTime;
            if (elapsed > timeUnit.toMillis(1)) {
                requestCount = 0;
                lastResetTime = System.currentTimeMillis();
            }
        }
    }
}