package catdevs.georuraldatahub.collection;

import java.util.List;
import java.util.ArrayList;
import catdevs.georuraldatahub.dto.DictionaryRequestDTO;
import catdevs.georuraldatahub.validation.FieldRule;
import catdevs.georuraldatahub.validation.FieldType;
import org.springframework.stereotype.Component;

@Component
public class CollectionCatalog {
    public record Entry(String id, String agency, String product, String access, String documentation, String limitations) {}
    public List<Entry> list() {
        return List.of(
            new Entry("IBGE_MUNICIPIOS", "IBGE", "Malha municipal do Paraná", "AUTOMATICO",
                "https://servicodados.ibge.gov.br/api/docs/malhas?versao=3",
                "Até 2022 usa a API simplificada; a partir de 2023 usa o ZIP estadual da Malha Municipal Digital, em SIRGAS 2000 geográfico. Saída preserva todos os atributos do Shapefile e acrescenta codarea como alias de CD_MUN; o ZIP original também é preservado. Exige edição publicada e código municipal iniciado por 41; não substitui cadastro fundiário."),
            new Entry("IBGE_BIOMAS", "IBGE", "Limites dos biomas no Paraná", "AUTOMATICO",
                "https://geoftp.ibge.gov.br/informacoes_ambientais/estudos_ambientais/biomas/vetores/",
                "Edições 2019 e 2025, escala 1:250.000, recortadas pelo limite IBGE do Paraná. Mantém coordenadas SIRGAS 2000; cálculo de área exige projeção equivalente posterior."),
            new Entry("INPE_PRODES", "INPE", "Incremento anual no Paraná, por bioma", "AUTOMATICO",
                "https://terrabrasilis.dpi.inpe.br/download-de-dados/",
                "WFS paginado com ano explícito e Cerrado ou Mata Atlântica, sempre recortado ao Paraná; bbox opcional só restringe mais. Limites operacionais abortam a coleta, sem salvar base truncada."),
            new Entry("INPE_QUEIMADAS", "INPE", "Focos diários no Paraná", "AUTOMATICO",
                "https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil/",
                "CSV diário nacional preservado, saída filtrada por estado_id=41 e município paranaense, todos os satélites; disponibilidade limitada à retenção do servidor. Focos não equivalem a área queimada."),
            new Entry("ICMBIO", "ICMBio", "UCs federais e embargos", "AGUARDA_HOMOLOGACAO",
                "https://www.gov.br/icmbio/pt-br/dados-icmbio/dados_geoespaciais",
                "Página oficial publica WFS na INDE e downloads SHP. Selecionar produto/camada e homologar antes de ativar; upload manual disponível."),
            new Entry("MMA", "MMA", "Unidades de conservação do CNUC", "AGUARDA_HOMOLOGACAO",
                "https://dados.mma.gov.br/dataset/unidadesdeconservacao",
                "Recursos ZIP por edição, incluindo links SharePoint sujeitos a mudanças. Não tratar página HTML como dado; upload manual disponível."),
            new Entry("FUNAI", "FUNAI", "Terras indígenas", "AGUARDA_HOMOLOGACAO",
                "https://www.gov.br/funai/pt-br/atuacao/terras-indigenas/geoprocessamento-e-mapas/geprocessamento",
                "GeoServer e downloads oficiais com atualização mensal; distinguir áreas em estudo das demais fases. Upload manual disponível."),
            new Entry("INCRA", "INCRA", "Territórios e assentamentos", "AGUARDA_HOMOLOGACAO",
                "https://acervofundiario.incra.gov.br/acervo/login.php",
                "Portal informa autenticação gov.br. Acesso automatizado sem autenticação não foi confirmado; upload manual disponível."),
            new Entry("SFB", "SFB", "Cadastro Nacional de Florestas Públicas", "AGUARDA_HOMOLOGACAO",
                "https://dados.florestal.gov.br/pt_BR/dataset/cadastro-nacional-de-florestas-publicas-cnfp",
                "Catálogo publica recursos por edição e geosserviços; CSV estatístico não substitui polígonos. Upload manual disponível."),
            new Entry("IBAMA", "IBAMA", "Áreas embargadas", "AGUARDA_HOMOLOGACAO",
                "https://pamgia.ibama.gov.br/server/rest/services/01_Publicacoes_Bases/adm_embargos_ibama_a/OGCFeatureServer/collections?f=html",
                "Serviço OGC público documentado; homologar camada, vigência e paginação. Não coletar dados pessoais além do necessário; upload manual disponível."),
            new Entry("CAR_SICAR", "CAR/SICAR", "Imóvel, APP, reserva legal e vegetação declarada", "MANUAL",
                "https://www.car.gov.br/publico/",
                "Mantido upload existente; nenhuma automação de login, captcha ou download não autorizado.")
        );
    }
    public DictionaryRequestDTO dictionaryTemplate(CollectionRequest.Product product, String competence) {
        List<FieldRule> rules = new ArrayList<>();
        switch (product) {
            case IBGE_MUNICIPIOS -> {
                rules.add(FieldRule.required("codarea", FieldType.STRING));
                if (competence != null && competence.matches("[0-9]{4}") && Integer.parseInt(competence) >= 2023) {
                    for (String field : List.of("CD_MUN", "NM_MUN", "CD_RGI", "NM_RGI", "CD_RGINT", "NM_RGINT",
                            "CD_UF", "NM_UF", "SIGLA_UF", "CD_REGIA", "NM_REGIA", "SIGLA_RG", "CD_CONCU", "NM_CONCU"))
                        rules.add(optional(field, FieldType.STRING));
                    rules.add(optional("AREA_KM2", FieldType.NUMBER));
                }
            }
            case IBGE_BIOMAS -> {
                if ("2019".equals(competence)) {
                    rules.add(FieldRule.required("Bioma", FieldType.STRING));
                    rules.add(FieldRule.required("CD_Bioma", FieldType.NUMBER));
                } else if ("2025".equals(competence)) {
                    rules.add(FieldRule.required("CD_BIOMA", FieldType.STRING));
                    rules.add(FieldRule.required("NM_BIOMA", FieldType.STRING));
                } else throw new IllegalArgumentException("Informe competence=2019 ou competence=2025 para o modelo de biomas.");
            }
            case INPE_PRODES -> {
                rules.add(new FieldRule("uuid", true, FieldType.STRING, List.of(), true));
                rules.add(FieldRule.required("year", FieldType.INTEGER));
                rules.add(new FieldRule("state", true, FieldType.STRING, List.of("PR", "PARANA", "PARANÁ"), false));
                for (String field : List.of("fid", "def_cloud", "julian_day", "scene_id", "area_km"))
                    rules.add(optional(field, FieldType.NUMBER));
                for (String field : List.of("path_row", "main_class", "class_name", "image_date", "source", "satellite", "sensor", "publish_year", "pub_date", "update_at"))
                    rules.add(optional(field, FieldType.STRING));
            }
            case INPE_QUEIMADAS -> {
                rules.add(new FieldRule("id", true, FieldType.STRING, List.of(), true));
                rules.add(FieldRule.required("lat", FieldType.LATITUDE));
                rules.add(FieldRule.required("lon", FieldType.LONGITUDE));
                rules.add(new FieldRule("estado_id", true, FieldType.INTEGER, List.of("41"), false));
                for (String field : List.of("data_hora_gmt", "satelite", "municipio", "estado", "pais", "bioma"))
                    rules.add(optional(field, FieldType.STRING));
                for (String field : List.of("municipio_id", "pais_id", "numero_dias_sem_chuva", "precipitacao", "risco_fogo", "frp"))
                    rules.add(optional(field, FieldType.NUMBER));
            }
        }
        return new DictionaryRequestDTO("Dicionário " + product + (competence == null ? "" : " " + competence),
                "Modelo para dados normalizados pelo conector. Revise antes de cadastrar; regras existentes não são alteradas automaticamente.", List.copyOf(rules));
    }
    private FieldRule optional(String name, FieldType type) { return new FieldRule(name, false, type, List.of(), false); }

    public Entry entry(CollectionRequest.Product product) {
        return list().stream().filter(e -> e.id().equals(product.name())).findFirst().orElseThrow();
    }
}
