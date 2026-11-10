package catdevs.georuraldatahub.collection;

import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** Completa a cadeia omitida pela FUNAI sem transformar o intermediário em raiz de confiança. */
final class OfficialTls {
    private OfficialTls() {}

    static SSLContext context() {
        try {
            var defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaults.init((KeyStore) null);
            var roots = new HashSet<TrustAnchor>();
            for (var manager : defaults.getTrustManagers())
                if (manager instanceof X509TrustManager x509)
                    for (var certificate : x509.getAcceptedIssuers()) roots.add(new TrustAnchor(certificate, null));
            var parameters = new PKIXBuilderParameters(roots, new X509CertSelector());
            // Mesmo comportamento de revogação do cliente JSSE padrão; assinaturas, validade e hostname continuam verificados.
            parameters.setRevocationEnabled(false);
            try (var input = OfficialTls.class.getResourceAsStream("/certificates/sectigo-ov-r36.pem")) {
                if (input == null) throw new IllegalStateException("Certificado intermediário oficial ausente.");
                var intermediate = CertificateFactory.getInstance("X.509").generateCertificate(input);
                parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(List.of(intermediate))));
            }
            var trust = TrustManagerFactory.getInstance("PKIX");
            trust.init(new CertPathTrustManagerParameters(parameters));
            var context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (Exception e) { throw new IllegalStateException("Não foi possível configurar a validação TLS dos canais oficiais.", e); }
    }
}
