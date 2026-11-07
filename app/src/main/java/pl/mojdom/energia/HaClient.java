package pl.mojdom.energia;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class HaClient {
    static String validate(String raw, boolean allowHttp) throws Exception {
        URI uri = new URI(raw.trim());
        String scheme = uri.getScheme();
        if ((!"https".equals(scheme) && !"http".equals(scheme)) || uri.getHost()==null
            || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new Exception("Podaj adres http:// lub https:// bez loginu, parametrów i fragmentu.");
        if ("http".equals(scheme) && !allowHttp) throw new Exception("HTTP wymaga zaznaczenia zgody na nieszyfrowane połączenie.");
        String result = uri.toString();
        while (result.endsWith("/")) result=result.substring(0,result.length()-1);
        return result;
    }
    static String get(String base, String token, String path) throws Exception { return request(base, token, path, null); }
    /** Wywołanie usługi HA, np. post(base, token, "/api/services/input_number/set_value", "{...}"). */
    static String post(String base, String token, String path, String json) throws Exception { return request(base, token, path, json); }
    private static String request(String base, String token, String path, String json) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(base+path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(7000); conn.setReadTimeout(10000);
        conn.setRequestProperty("Authorization","Bearer "+token);
        conn.setRequestProperty("Accept","application/json");
        try {
            if (json!=null) {
                conn.setRequestMethod("POST"); conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json");
                try (OutputStream os=conn.getOutputStream()) { os.write(json.getBytes(StandardCharsets.UTF_8)); }
            }
            int code=conn.getResponseCode();
            if (code==401 || code==403) throw new Exception("Brak dostępu — sprawdź token i uprawnienia konta.");
            if (code>=300 && code<400) throw new Exception("Serwer przekierowuje. Wpisz docelowy adres HA.");
            if (code!=200) throw new Exception("Home Assistant: HTTP "+code);
            try (InputStream in=conn.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] b=new byte[8192]; int n;
                while ((n=in.read(b))!=-1) { if(out.size()+n>16*1024*1024) throw new Exception("Odpowiedź jest zbyt duża."); out.write(b,0,n); }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (javax.net.ssl.SSLException e) { throw new Exception("Błąd certyfikatu HTTPS. Sprawdź adres i certyfikat serwera."); }
        catch (SocketTimeoutException e) { throw new Exception("Serwer nie odpowiedział. Sprawdź Wi-Fi lub VPN."); }
        catch (UnknownHostException e) { throw new Exception("Nie znaleziono serwera. Sprawdź adres i sieć."); }
        finally { conn.disconnect(); }
    }
}
