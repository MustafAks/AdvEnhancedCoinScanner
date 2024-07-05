package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int RATE_LIMIT_SLEEP_TIME_MS = 10000;  // 10 seconds

    public static void main(String[] args) {
        String botToken = "6482508265:AAEDUmyCM-ygU7BVO-txyykS7cKn5URspmY";  // Replace with your actual bot token
        long chatId = 1692398446;           // Replace with your actual chat ID

        while (true) {
            try {
                // Popüler coinleri çek
                JSONArray trendingCoins = getTrendingCoins();

                // Hype tespit et
                List<String> hypeCoins = detectHypeCoins(trendingCoins);

                // Eğer hype coinler varsa Telegram'a gönder
                if (!hypeCoins.isEmpty()) {
                    sendHypeNotification(botToken, chatId, hypeCoins, trendingCoins);
                }

                // En çok değer kazanan ve kaybeden coinler
                JSONArray topCoins = getTopCoins();
                JSONArray topGainers = getTopGainers(topCoins);
                JSONArray topLosers = getTopLosers(topCoins);
                sendTopGainersAndLosers(botToken, chatId, topGainers, topLosers);

                // 1 saat bekle
                System.out.println("Waiting for 2 hours before next execution...");
                Thread.sleep(7200000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static JSONArray getTrendingCoins() {
        String urlString = "https://api.coingecko.com/api/v3/search/trending";
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getJSONArray("coins");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static JSONArray getTopCoins() {
        String urlString = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1";
        int retries = 5; // Retry 5 times in case of a 429 error
        int sleepTime = RATE_LIMIT_SLEEP_TIME_MS;

        while (retries > 0) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    System.out.println("Rate limit exceeded. Waiting before retrying...");
                    Thread.sleep(sleepTime); // Wait before retrying
                    retries--;
                    sleepTime *= 2; // Increase sleep time exponentially
                    continue;
                } else if (responseCode != 200) {
                    System.err.println("Failed to fetch top coins. Response Code: " + responseCode);
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return new JSONArray(response.toString());

            } catch (Exception e) {
                e.printStackTrace();
                retries--;
                sleepTime *= 2; // Increase sleep time exponentially
                if (retries == 0) {
                    System.err.println("Failed to fetch top coins.");
                    return null; // Return null if all retries fail
                }
            }
        }
        return null;
    }

    public static JSONArray getTopGainers(JSONArray allCoins) {
        List<JSONObject> gainers = new ArrayList<>();
        for (int i = 0; i < allCoins.length(); i++) {
            JSONObject coin = allCoins.getJSONObject(i);
            gainers.add(coin);
        }
        gainers.sort((a, b) -> Double.compare(b.optDouble("price_change_percentage_24h", 0), a.optDouble("price_change_percentage_24h", 0)));
        return new JSONArray(gainers.subList(0, Math.min(gainers.size(), 10)));
    }

    public static JSONArray getTopLosers(JSONArray allCoins) {
        List<JSONObject> losers = new ArrayList<>();
        for (int i = 0; i < allCoins.length(); i++) {
            JSONObject coin = allCoins.getJSONObject(i);
            losers.add(coin);
        }
        losers.sort((a, b) -> Double.compare(a.optDouble("price_change_percentage_24h", 0), b.optDouble("price_change_percentage_24h", 0)));
        return new JSONArray(losers.subList(0, Math.min(losers.size(), 10)));
    }

    public static JSONObject getCoinDetails(String coinId) {
        String urlString = "https://api.coingecko.com/api/v3/coins/" + coinId;
        int retries = 5; // Retry 5 times in case of a 429 error
        int sleepTime = RATE_LIMIT_SLEEP_TIME_MS;

        while (retries > 0) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    System.out.println("Rate limit exceeded. Waiting before retrying...");
                    Thread.sleep(sleepTime); // Wait before retrying
                    retries--;
                    sleepTime *= 2; // Increase sleep time exponentially
                    continue;
                } else if (responseCode != 200) {
                    System.err.println("Failed to fetch details for coin: " + coinId + ". Response Code: " + responseCode);
                    return null;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                return new JSONObject(response.toString());

            } catch (Exception e) {
                e.printStackTrace();
                retries--;
                sleepTime *= 2; // Increase sleep time exponentially
                if (retries == 0) {
                    System.err.println("Failed to fetch details for coin: " + coinId);
                    return null; // Return null if all retries fail
                }
            }
        }
        return null;
    }

    public static List<String> detectHypeCoins(JSONArray coins) {
        List<String> hypeCoins = new ArrayList<>();
        for (int i = 0; i < coins.length(); i++) {
            JSONObject coin = coins.getJSONObject(i).getJSONObject("item");

            // Coin detaylarını al
            JSONObject coinDetails = getCoinDetails(coin.getString("id"));
            if (coinDetails == null) {
                System.out.println("Failed to fetch details for coin: " + coin.getString("id"));
                continue;
            }

            // `price_change_percentage_24h` ve `total_volume` değerlerini coinDetails'ten al
            double priceChangePercentage24h = coinDetails
                    .getJSONObject("market_data")
                    .optDouble("price_change_percentage_24h", 0.0);

            int twitterFollowers = coinDetails
                    .getJSONObject("community_data")
                    .optInt("twitter_followers", 0);

            // Konsola hacim verisini yazdırarak kontrol edelim
            System.out.println("Coin: " + coin.getString("name") + ", Price Change 24h: " + priceChangePercentage24h + "%, Twitter Followers: " + twitterFollowers);

            // Örnek olarak, fiyatı son 24 saatte %20'den fazla artan ve Twitter takipçisi 10,000'den fazla artan coinleri hype olarak kabul edelim.
            if (priceChangePercentage24h > 0.2 && twitterFollowers > 10000) {
                hypeCoins.add(coin.getString("symbol")); // Coin ismi yerine sembolünü kullan
            }
        }
        return hypeCoins;
    }

    public static void sendHypeNotification(String botToken, long chatId, List<String> hypeCoins, JSONArray trendingCoins) {
        try {
            // Resim oluştur
            BufferedImage image = createImage(trendingCoins, hypeCoins, "TOP TRENDING COINS");
            File imageFile = new File("trending_coins.png");
            ImageIO.write(image, "png", imageFile);

            // Telegram API'sine resim gönder
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendPhoto";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream outputStream = connection.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

                // Add chat_id
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                writer.append(String.valueOf(chatId)).append("\r\n");

                // Add caption
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                writer.append("TOP TRENDING COINS\r\n");

                // Add photo
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"trending_coins.png\"\r\n");
                writer.append("Content-Type: image/png\r\n\r\n");
                writer.flush();

                Files.copy(imageFile.toPath(), outputStream);
                outputStream.flush();

                writer.append("\r\n");
                writer.append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Telegram notification sent successfully.");
            } else {
                System.err.println("Error sending notification to Telegram. Response code: " + responseCode);
                // Hata durumunda, sunucudan gelen hatayı okumak
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println(line);
                    }
                }
            }

            // Metin mesajı gönder
            sendTextMessage(botToken, chatId, trendingCoins);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendTextMessage(String botToken, long chatId, JSONArray trendingCoins) {
        try {
            StringBuilder message = new StringBuilder();
            for (int i = 0; i < trendingCoins.length(); i++) {
                JSONObject coin = trendingCoins.getJSONObject(i).getJSONObject("item");
                message.append("$").append(coin.getString("symbol")).append(" "); // Coin ismi yerine sembolünü kullan
            }
            // Etiketler ekle
            message.append("\n$crypto $HODLr $Coinz $Token $CoinX $AltUp");

            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("chat_id", chatId);
            jsonMessage.put("text", message.toString());

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = jsonMessage.toString().getBytes("utf-8");
                outputStream.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Text message sent successfully.");
            } else {
                System.err.println("Error sending text message to Telegram. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendTopGainersAndLosers(String botToken, long chatId, JSONArray topGainers, JSONArray topLosers) {
        try {
            // Resim oluştur
            BufferedImage image = createTopGainersAndLosersImage(topGainers, topLosers);
            File imageFile = new File("top_gainers_losers.png");
            ImageIO.write(image, "png", imageFile);

            // Telegram API'sine resim gönder
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendPhoto";
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream outputStream = connection.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {

                // Add chat_id
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
                writer.append(String.valueOf(chatId)).append("\r\n");

                // Add caption
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
                writer.append("TOP GAINERS AND LOSERS\r\n");

                // Add photo
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"top_gainers_losers.png\"\r\n");
                writer.append("Content-Type: image/png\r\n\r\n");
                writer.flush();

                Files.copy(imageFile.toPath(), outputStream);
                outputStream.flush();

                writer.append("\r\n");
                writer.append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Telegram notification sent successfully.");
            } else {
                System.err.println("Error sending notification to Telegram. Response code: " + responseCode);
                // Hata durumunda, sunucudan gelen hatayı okumak
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println(line);
                    }
                }
            }

            // Metin mesajı gönder
            sendTextMessageGainersAndLosers(botToken, chatId, topGainers, topLosers);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendTextMessageGainersAndLosers(String botToken, long chatId, JSONArray topGainers, JSONArray topLosers) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("Top Gainers:\n");
            for (int i = 0; i < topGainers.length(); i++) {
                JSONObject gainer = topGainers.getJSONObject(i);
                message.append("$").append(gainer.getString("symbol")).append(" "); // Coin ismi yerine sembolünü kullan
            }
            message.append("\nTop Losers:\n");
            for (int i = 0; i < topLosers.length(); i++) {
                JSONObject loser = topLosers.getJSONObject(i);
                message.append("$").append(loser.getString("symbol")).append(" "); // Coin ismi yerine sembolünü kullan
            }

            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("chat_id", chatId);
            jsonMessage.put("text", message.toString());

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = jsonMessage.toString().getBytes("utf-8");
                outputStream.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Text message sent successfully.");
            } else {
                System.err.println("Error sending text message to Telegram. Response code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage createImage(JSONArray trendingCoins, List<String> hypeCoins, String title) {
        int coinCount = trendingCoins.length();
        int boxHeight = 100; // Çerçeve yüksekliği
        int padding = 10; // Çerçeve içi boşluk
        int initialHeight = 1000;
        int height = Math.max(initialHeight, coinCount * (boxHeight + padding) + 150); // Yüksekliği dinamik olarak ayarla

        int width = 800;
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        // Arkaplanı beyaz yap
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Başlığı ortala ve ekle
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, 40);

        // Coinleri listele
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        int yPosition = 80;
        for (int i = 0; i < trendingCoins.length(); i++) {
            JSONObject coin = trendingCoins.getJSONObject(i).getJSONObject("item");
            String coinName = coin.getString("name");
            String coinSymbol = coin.getString("symbol");

            JSONObject coinDetails = getCoinDetails(coin.getString("id"));
            if (coinDetails == null) {
                continue;
            }

            double priceChangePercentage24h = coinDetails
                    .getJSONObject("market_data")
                    .optDouble("price_change_percentage_24h", 0.0);

            String imageUrl = coinDetails.optJSONObject("image").optString("large", ""); // Check if image key exists

            String hypeIndicator = hypeCoins.contains(coinSymbol) ? " (HYPE)" : ""; // Coin ismi yerine sembolünü kullan
            String coinInfo = String.format("%s (%s)%s - 24h Change: %.2f%%", coinName, coinSymbol, hypeIndicator, priceChangePercentage24h);

            // Coin ismi ve fiyat değişimini yaz
            g2d.drawString(coinInfo, 100, yPosition + 40);

            // Coin imajını ekle
            BufferedImage coinImage = null;
            if (!imageUrl.isEmpty()) {
                try {
                    URL imageURL = new URL(imageUrl);
                    coinImage = ImageIO.read(imageURL);
                    if (coinImage != null) {
                        g2d.drawImage(coinImage, 30, yPosition + 10, 60, 60, null);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            yPosition += boxHeight;
        }

        g2d.dispose();
        return bufferedImage;
    }

    private static BufferedImage createTopGainersAndLosersImage(JSONArray topGainers, JSONArray topLosers) {
        int itemCount = Math.max(topGainers.length(), topLosers.length());
        int boxHeight = 50;
        int padding = 10;
        int imageHeight = itemCount * (boxHeight + padding) + 150; // Yüksekliği dinamik olarak ayarla

        int width = 800;
        BufferedImage bufferedImage = new BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();

        // Arkaplanı beyaz yap
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, imageHeight);

        // Başlığı ortala ve ekle
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth("TOP GAINERS AND LOSERS");
        g2d.drawString("TOP GAINERS AND LOSERS", (width - titleWidth) / 2, 40);

        // Gainers başlığı ekle
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Top Gainers", 100, 80);

        // Losers başlığı ekle
        g2d.drawString("Top Losers", 500, 80);

        // Gainers ve Losers listele
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        int yPosition = 100;
        for (int i = 0; i < itemCount; i++) {
            if (i < topGainers.length()) {
                JSONObject gainer = topGainers.getJSONObject(i);
                String gainerInfo = String.format("%s (%s) - 24h Change: %.2f%%", gainer.getString("name"), gainer.getString("symbol"), gainer.getDouble("price_change_percentage_24h"));
                g2d.drawString(gainerInfo, 100, yPosition + 40);
                String imageUrl = gainer.optString("image", ""); // Check if image key exists
                BufferedImage coinImage = null;
                if (!imageUrl.isEmpty()) {
                    try {
                        URL imageURL = new URL(imageUrl);
                        coinImage = ImageIO.read(imageURL);
                        if (coinImage != null) {
                            g2d.drawImage(coinImage, 30, yPosition + 10, 30, 30, null); // Küçültülmüş resim boyutu
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (i < topLosers.length()) {
                JSONObject loser = topLosers.getJSONObject(i);
                String loserInfo = String.format("%s (%s) - 24h Change: %.2f%%", loser.getString("name"), loser.getString("symbol"), loser.getDouble("price_change_percentage_24h"));
                g2d.drawString(loserInfo, 500, yPosition + 40);
                String imageUrl = loser.optString("image", ""); // Check if image key exists
                BufferedImage coinImage = null;
                if (!imageUrl.isEmpty()) {
                    try {
                        URL imageURL = new URL(imageUrl);
                        coinImage = ImageIO.read(imageURL);
                        if (coinImage != null) {
                            g2d.drawImage(coinImage, 430, yPosition + 10, 30, 30, null); // Küçültülmüş resim boyutu
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            yPosition += boxHeight;
        }

        g2d.dispose();
        return bufferedImage;
    }

}
