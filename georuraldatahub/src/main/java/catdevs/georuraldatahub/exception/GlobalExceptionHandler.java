package catdevs.georuraldatahub.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.oracle.bmc.model.BmcException;
import java.io.IOException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders())
                .body(ex.getReason() == null ? "Não foi possível concluir a operação." : ex.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleInvalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body("Corpo JSON inválido. Confira os tipos, os IDs e o identificador do produto informado.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleUploadLimit(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body("O arquivo excede o limite de upload configurado.");
    }

    @ExceptionHandler(BmcException.class)
    public ResponseEntity<String> handleStorage(BmcException ex) {
        LOG.error("Falha no Object Storage OCI", ex);
        if (ex.getStatusCode() == 404) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Objeto não encontrado no bucket configurado.");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Não foi possível acessar o armazenamento OCI. Confira a configuração do bucket e as permissões do backend.");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleReadFailure(IOException ex) {
        LOG.error("Falha ao ler ou armazenar arquivo", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Não foi possível ler ou armazenar o arquivo. Confira o formato e a disponibilidade do armazenamento.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDatabaseConstraint(DataIntegrityViolationException ex) {
        LOG.error("Restrição do banco ao salvar dados", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Os dados não puderam ser salvos por uma restrição do banco. Confira os IDs, os limites dos campos e o usuário informado.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<String> handleBusyResource(PessimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("A fonte ou o conjunto está ocupado por outra operação. Aguarde e tente novamente.");
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<String> handleInvalidParameter(Exception ex) {
        return ResponseEntity.badRequest().body("Parâmetro inválido ou obrigatório ausente. Confira os IDs, o produto e o campo multipart file.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception ex) {
        // Preserve Spring HTTP statuses (missing parameters, unsupported methods, etc.).
        if (ex instanceof ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                    .body(error.getBody().getDetail() == null ? "Requisição inválida." : error.getBody().getDetail());
        }
        LOG.error("Falha ao concluir requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Não foi possível concluir a operação. Consulte os logs do backend para identificar a causa.");
    }

    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<String> handleVersionNotFound(VersionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<String> handleDuplicateFile(DuplicateFileException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(DataSetNotFoundException.class)
    public ResponseEntity<String> handleDatasetNotFound(DataSetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Dados inválidos");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ex.getMessage());
    }
}