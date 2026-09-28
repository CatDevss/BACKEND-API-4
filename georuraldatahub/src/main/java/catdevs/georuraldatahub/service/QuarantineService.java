package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Quarantine;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.repository.QuarantineRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuarantineService {

    private static final String STATUS_PENDENTE = "PENDENTE";

    private final QuarantineRepository quarantineRepository;

    public QuarantineService(QuarantineRepository quarantineRepository) {
        this.quarantineRepository = quarantineRepository;
    }

    // TODO(T3.1 - pendente migração): quando existir a coluna que localiza o
    // registro dentro do arquivo bruto (ex.: qua_indice_registro), este método
    // passa a receber também esse índice e gravá-lo aqui.
    public Quarantine sendToQuarantine(File file, User user, String reason) {
        Quarantine quarantine = new Quarantine(
                file,
                user,
                STATUS_PENDENTE,
                reason,
                LocalDateTime.now()
        );
        return quarantineRepository.save(quarantine);
    }

    public List<Quarantine> listByDataset(Long datasetId) {
        return quarantineRepository.findByFile_Version_Dataset_IdOrderByDateEntryDesc(datasetId);
    }
}