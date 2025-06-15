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

        /* Para POSIX */
        PosixFileAttributeView posixView = Files.getFileAttributeView(caminho, PosixFileAttributeView.class);
        if (posixView != null) {
            return cache.computeIfAbsent(caminho, p -> {
                try {
                    return extrairId(posixView.readAttributes().fileKey().toString(), INO_PATTERN);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        /* Para Windows */
        DosFileAttributeView dosView = Files.getFileAttributeView(caminho, DosFileAttributeView.class);
        if (dosView != null) {
            return cache.computeIfAbsent(caminho, p -> {
                try {
                    return extrairId(dosView.readAttributes().fileKey().toString(), INDEX_PATTERN);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        /* Para outros sistemas operacionais */
        BasicFileAttributes basic = Files.readAttributes(caminho, BasicFileAttributes.class);
        Object key = basic.fileKey();
        if (key != null) {
            String strKey = key.toString();
            String id = Integer.toString(Math.abs(strKey.hashCode()));
            gerarMensagemLog("ID genérico obtido: " + id + " (baseado em " + strKey + ")");
            return id;
        }

        /* Caso nenhum desses funcione, há uma forma conforme o tamanho e a data de última modificação da pasta */
        try {
            String c = pasta.getAbsolutePath();
            long ultimaModificacao = pasta.lastModified();
            long tamanho = calcularTamanhoPasta(pasta);

            int hash = (c + ultimaModificacao + tamanho).hashCode();
            return String.valueOf(Math.abs(hash));
        }
        catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public static String extrairNomeOriginal (String pasta) {
        int ultimoUnderline = pasta.lastIndexOf("_");
        if (ultimoUnderline > 0) {
            return pasta.substring(0, ultimoUnderline);
        }
        return pasta;
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