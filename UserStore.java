// UserStore.java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class UserStore {
    private final Path usersFile = Paths.get(System.getProperty("user.home"), ".skillnest_users");

    boolean registerUser(String username, String password) throws IOException {
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("username");
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("password");
        Map<String,String> map = loadAll();
        if (map.containsKey(username)) return false; // already exists
        String hash = hashPassword(password);
        String line = username + "," + hash + "\n";
        Files.write(usersFile, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return true;
    }

    boolean authenticateUser(String username, String password) throws IOException {
        Map<String,String> map = loadAll();
        String stored = map.get(username);
        if (stored == null) return false;
        String hash = hashPassword(password);
        return stored.equals(hash);
    }

    private Map<String,String> loadAll() throws IOException {
        Map<String,String> m = new HashMap<>();
        if (Files.notExists(usersFile)) return m;
        List<String> lines = Files.readAllLines(usersFile, StandardCharsets.UTF_8);
        for (String ln : lines) {
            if (ln.trim().isEmpty()) continue;
            int idx = ln.indexOf(',');
            if (idx < 0) continue;
            String u = ln.substring(0, idx);
            String h = ln.substring(idx+1).trim();
            m.put(u, h);
        }
        return m;
    }

    private String hashPassword(String pwd) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(pwd.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : b) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (Exception e) { return Integer.toString(pwd.hashCode()); }
    }
}
