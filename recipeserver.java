// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class recipeserver {
   public recipeserver() {
   }

   static String loadApiKey() throws IOException {
      for(String var1 : Files.readAllLines(Path.of(".env"))) {
         if (var1.startsWith("GEMINI_API_KEY=")) {
            return var1.substring("GEMINI_API_KEY=".length()).trim();
         }
      }

      throw new RuntimeException("GEMINI_API_KEY not found in .env");
   }

   public static void main(String[] var0) throws IOException {
      String var1 = loadApiKey();
      HttpServer var2 = HttpServer.create(new InetSocketAddress(3000), 0);
      var2.createContext("/", (var0x) -> {
         if (!var0x.getRequestMethod().equalsIgnoreCase("GET")) {
            var0x.sendResponseHeaders(405, -1L);
         } else {
            File var1 = new File("index.html");
            byte[] var2 = Files.readAllBytes(var1.toPath());
            var0x.getResponseHeaders().set("Content-Type", "text/html");
            var0x.sendResponseHeaders(200, (long)var2.length);
            var0x.getResponseBody().write(var2);
            var0x.getResponseBody().close();
         }
      });
      var2.createContext("/api/analyze-ingredients", (var1x) -> {
         var1x.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
         var1x.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
         var1x.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
         if (var1x.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            var1x.sendResponseHeaders(204, -1L);
         } else {
            try {
               String var2 = new String(var1x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
               String var15 = var2.replaceAll(".*\\[", "").replaceAll("].*", "").replace("\"", "");
               String var16 = "You are a creative chef. Given these leftover ingredients: " + var15 + ", suggest ONE practical recipe. Include: recipe name, prep time, step-by-step numbered instructions, and helpful tips. Keep it concise but easy to follow.";
               String var21 = var16.replace("\"", "\\\\\"");
               String var5 = "{\"contents\":[{\"parts\":[{\"text\":\"" + var21.replace("\n", "\\n") + "\"}]}]}";
               HttpClient var6 = HttpClient.newHttpClient();
               HttpRequest var7 = HttpRequest.newBuilder().uri(URI.create("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=" + var1)).header("Content-Type", "application/json").POST(BodyPublishers.ofString(var5)).build();
               HttpResponse var8 = var6.send(var7, BodyHandlers.ofString());
               if (var8.statusCode() != 200) {
                  String var17 = "{\"error\": \"API returned status " + var8.statusCode() + "\"}";
                  byte[] var18 = var17.getBytes(StandardCharsets.UTF_8);
                  var1x.sendResponseHeaders(500, (long)var18.length);
                  var1x.getResponseBody().write(var18);
                  var1x.getResponseBody().close();
                  return;
               }

               String var9 = (String)var8.body();
               System.out.println(var9);
               String var10 = "No recipe generated.";
               int var11 = var9.indexOf("\"text\":");
               if (var11 != -1) {
                  int var12 = var9.indexOf("\"", var11 + 7) + 1;
                  int var13 = var9.indexOf("\"", var12);
                  if (var12 > 0 && var13 > var12) {
                     var10 = var9.substring(var12, var13);
                  }
               }

               String var19 = "{ \"recipes\": \"" + var10.replace("\"", "\\\"") + "\" }";
               byte[] var20 = var19.getBytes(StandardCharsets.UTF_8);
               var1x.getResponseHeaders().set("Content-Type", "application/json");
               var1x.sendResponseHeaders(200, (long)var20.length);
               var1x.getResponseBody().write(var20);
               var1x.getResponseBody().close();
            } catch (Exception var14) {
               String var10000 = var14.getMessage();
               String var3 = "{\"error\": \"" + var10000.replace("\"", "'") + "\"}";
               byte[] var4 = var3.getBytes(StandardCharsets.UTF_8);
               var1x.sendResponseHeaders(500, (long)var4.length);
               var1x.getResponseBody().write(var4);
               var1x.getResponseBody().close();
            }

         }
      });
      var2.start();
      System.out.println("✅ Leftover Lab running at http://localhost:3000");
   }
}
