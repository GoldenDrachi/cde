import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Extractor {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("C:/Users/golde/.gemini/antigravity-ide/brain/dfa4b028-4879-431d-ab3b-eadfe3a47338/.system_generated/logs/transcript_full.jsonl"));
        for (String line : lines) {
            if (line.contains("\"type\":\"TOOL_RESPONSE\"") && line.contains("DungeonGenerator.kt")) {
                System.out.println("FOUND DUNGEON GENERATOR CONTENT!!!");
                // Extract just a bit to verify
                System.out.println(line.substring(0, Math.min(line.length(), 150)));
            } else if (line.contains("\"CommandLine\"") && line.contains("DungeonGenerator.kt")) {
                System.out.println("COMMAND LINE: " + line.substring(0, Math.min(line.length(), 300)));
            } else if (line.contains("\"name\":\"view_file\"") && line.contains("DungeonGenerator.kt")) {
                System.out.println("VIEW FILE: " + line.substring(0, Math.min(line.length(), 300)));
            } else if (line.contains("\"name\":\"replace_file_content\"") && line.contains("DungeonGenerator.kt")) {
                System.out.println("REPLACE FILE CONTENT: " + line.substring(0, Math.min(line.length(), 300)));
            } else if (line.contains("\"name\":\"multi_replace_file_content\"") && line.contains("DungeonGenerator.kt")) {
                System.out.println("MULTI REPLACE FILE CONTENT: " + line.substring(0, Math.min(line.length(), 300)));
            }
        }
    }
}
