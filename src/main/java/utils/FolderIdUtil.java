package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;

public class FolderIdUtil {
    // Cache simples para evitar recálculo: Path -> ID
    private static final ConcurrentHashMap<Path, String> cache = new ConcurrentHashMap<>();

    /**
     * Gera um ID único e consistente para uma pasta, de forma compatível com qualquer sistema operacional.
     * A estratégia prioriza o 'fileKey' do sistema de arquivos, que é um identificador único.
     * Se não estiver disponível, usa uma combinação de caminho, data e tamanho para criar um hash.
     *
     * @param pasta O diretório para o qual o ID será gerado.
     * @return Uma String que representa o ID único da pasta.
     * @throws IOException Se ocorrer um erro de I/O ao ler os atributos da pasta.
     */
    public static String obterId(File pasta) throws IOException {
        Path caminho = pasta.toPath();

        // Verifica o cache primeiro para otimizar o desempenho.
        if (cache.containsKey(caminho)) {
            return cache.get(caminho);
        }

        String id;
        try {
            // 1. Tenta usar o método padrão e mais confiável (fileKey)
            BasicFileAttributes attrs = Files.readAttributes(caminho, BasicFileAttributes.class);
            Object fileKey = attrs.fileKey();

            if (fileKey != null) {
                // Usa o hash do fileKey, que é a forma mais robusta e portável.
                id = Integer.toString(Math.abs(fileKey.hashCode()));
                gerarMensagemLog("ID gerado via fileKey: " + id + " para a pasta " + pasta.getName());
            } else {
                // 2. Se fileKey não for suportado, usa o método de fallback.
                id = gerarIdPorFallback(pasta);
            }
        } catch (Exception e) {
            // 3. Em caso de qualquer erro, usa o método de fallback como segurança.
            gerarMensagemLog("Erro ao obter fileKey: " + e.getMessage() + ". Usando método de fallback.");
            id = gerarIdPorFallback(pasta);
        }

        // Armazena o ID gerado no cache antes de retorná-lo.
        cache.put(caminho, id);
        return id;
    }

    /**
     * Método de fallback para gerar um ID quando o 'fileKey' não está disponível.
     * Cria um hash a partir do caminho absoluto, data da última modificação e tamanho total da pasta.
     *
     * @param pasta A pasta para a qual o ID será gerado.
     * @return Um ID em String baseado no hash dos atributos.
     */
    private static String gerarIdPorFallback(File pasta) {
        try {
            String caminhoAbsoluto = pasta.getAbsolutePath();
            long ultimaModificacao = pasta.lastModified();
            long tamanho = calcularTamanhoPasta(pasta);

            // Combina os atributos em uma única String para gerar o hash.
            String baseParaHash = caminhoAbsoluto + ultimaModificacao + tamanho;
            int hash = baseParaHash.hashCode();
            String id = String.valueOf(Math.abs(hash));

            gerarMensagemLog("ID de fallback gerado: " + id + " para a pasta " + pasta.getName());
            return id;
        } catch (Exception e) {
            // Como último recurso absoluto, usa o tempo atual para garantir um valor único.
            String id = String.valueOf(System.currentTimeMillis());
            gerarMensagemLog("ERRO no fallback. Usando ID de emergência (timestamp): " + id);
            return id;
        }
    }

    /**
     * Extrai o nome original da pasta a partir de um nome combinado (nome_id).
     * @param nomeComId O nome da pasta no formato "nomeOriginal_id".
     * @return Apenas o nome original da pasta.
     */
    public static String extrairNomeOriginal(String nomeComId) {
        int ultimoUnderline = nomeComId.lastIndexOf('_');
        if (ultimoUnderline > 0) {
            return nomeComId.substring(0, ultimoUnderline);
        }
        return nomeComId;
    }

    /**
     * Calcula o tamanho total de uma pasta somando o tamanho de todos os arquivos internos, recursivamente.
     * @param pasta O diretório a ser medido.
     * @return O tamanho total da pasta em bytes.
     */
    private static long calcularTamanhoPasta(File pasta) {
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

    private static void gerarMensagemLog(String msg) {
        // A sua implementação de log pode ser usada aqui.
        System.out.println("[FolderIdUtil] " + msg);
    }
}