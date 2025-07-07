package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;

public class FolderIdUtil {
    private static final ConcurrentHashMap<Path, String> cache = new ConcurrentHashMap<>();

    public static String obterId(File pasta) throws IOException {
        Path caminho = pasta.toPath();

        if (cache.containsKey(caminho)) {
            return cache.get(caminho);
        }

        String id;
        try {
            BasicFileAttributes attrs = Files.readAttributes(caminho, BasicFileAttributes.class);
            Object fileKey = attrs.fileKey();

            if (fileKey != null) {
                id = Integer.toString(Math.abs(fileKey.hashCode()));
            } else {
                id = gerarIdPorFallback(pasta);
            }
        } catch (Exception e) {
            id = gerarIdPorFallback(pasta);
        }

        cache.put(caminho, id);
        return id;
    }

    private static String gerarIdPorFallback(File pasta) {
        try {
            String caminhoAbsoluto = pasta.getAbsolutePath();
            long ultimaModificacao = pasta.lastModified();
            long tamanho = calcularTamanhoPasta(pasta);

            String baseParaHash = caminhoAbsoluto + ultimaModificacao + tamanho;
            int hash = baseParaHash.hashCode();
            return String.valueOf(Math.abs(hash));
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public static String extrairNomeOriginal(String nomeComId) {
        int ultimoUnderline = nomeComId.lastIndexOf('_');
        if (ultimoUnderline > 0) {
            String supostoId = nomeComId.substring(ultimoUnderline + 1);
            if (supostoId.matches("\\d+")) {
                return nomeComId.substring(0, ultimoUnderline);
            }
        }
        return nomeComId;
    }

    public static String extrairId(String nomeComId) {
        int ultimoUnderline = nomeComId.lastIndexOf('_');
        if (ultimoUnderline > 0) {
            String supostoId = nomeComId.substring(ultimoUnderline + 1);
            // Garante que estamos extraindo algo que parece um ID
            if (supostoId.matches("\\d+")) {
                return supostoId;
            }
        }
        return "";
    }


    public static long calcularTamanhoPasta(File pasta) {
        long tamanho = 0;
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    tamanho += calcularTamanhoPasta(arquivo);
                } else {
                    tamanho += arquivo.length();
                }
            }
        }
        return tamanho;
    }
}