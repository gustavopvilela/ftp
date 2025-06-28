package utils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

public class FolderIdUtil {
    private static final Pattern INDEX_PATTERN = Pattern.compile("index=(\\d+)");
    private static final Pattern INO_PATTERN = Pattern.compile("ino=(\\d+)");

    // Cache simples: Path -> ID
    private static final ConcurrentHashMap<Path, String> cache = new ConcurrentHashMap<>();

    public static String obterId(File pasta) throws IOException {
        Path caminho = pasta.toPath();

        return cache.computeIfAbsent(caminho, p ->{
            try {
                DosFileAttributes attrs = Files.readAttributes(p, DosFileAttributes.class);

                Object fileKey = attrs.fileKey();

                if (fileKey != null) {
                    return fileKey.toString();
                } else {
                    return "path" + Math.abs(p.toAbsolutePath().toString().hashCode());
                }

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

    }

    public static String extrairNomeOriginal (String pasta) {

        if (pasta == null || pasta.isEmpty()) {
            return "";
        }
        int ultimoUnderline = pasta.lastIndexOf("_");
        if (ultimoUnderline == -1 || ultimoUnderline == 0) {
            return pasta;
        }
        return pasta.substring(0, ultimoUnderline);
    }

    private static String extrairId(String rawKey, Pattern pattern) {
        gerarMensagemLog("Raw fileKey: " + rawKey);
        Matcher m = pattern.matcher(rawKey);
        if (m.find()) {
            return m.group(1);
        }
        // fallback ao hash
        return Integer.toString(Math.abs(rawKey.hashCode()));
    }

    private static long calcularTamanhoPasta (File pasta) {
        long tamanho = 0;
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    tamanho += calcularTamanhoPasta(arquivo);
                }
                else {
                    tamanho += arquivo.length();
                }
            }
        }

        return tamanho;
    }

    private static void gerarMensagemLog(String msg) {
        // Reaproveite sua implementação de log
        System.out.println(msg);
    }
}