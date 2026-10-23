package catdevs.georuraldatahub.entity;

import jakarta.persistence.*; 
import java.time.LocalDateTime;
 import org.hibernate.annotations.CreationTimestamp;

@Entity @Table(name="fonte_coleta")
public class CollectionExecution {
 @Id 
 @GeneratedValue(strategy=GenerationType.IDENTITY) 
 @Column(name="col_id") 
 private Long id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) 
 @JoinColumn(name="fon_id",nullable=false) 
 private Source source;

 @ManyToOne(fetch=FetchType.LAZY,optional=false) 
 @JoinColumn(name="usr_id",nullable=false) 
 private User requester;

 @Column(name="col_status",nullable=false,length=20) 
 private String status="EM_EXECUCAO";

 @Column(name="col_hash",length=64) 
 private String hash;

 @Column(name="col_objeto",length=1000) 
 private String objectName;

 @Column(name="col_http_status") 
 private Integer httpStatus;

 @Column(name="col_erro",length=1000) 
 private String error;

 @CreationTimestamp 
 @Column(name="col_inicio",nullable=false,updatable=false) 
 private LocalDateTime startedAt;

 @Column(name="col_fim",updatable=false) 
 private LocalDateTime finishedAt;

 public Long getId(){
    return id;
} 

public Source getSource(){
    return source;
} 

public void setSource(Source v){
    source=v;
} 

public User getRequester(){
    return requester;
} 

public void setRequester(User v){
    requester=v;
} 

public String getStatus(){
    return status;
} 

public void setStatus(String v){
    status=v;
}

public String getHash(){
    return hash;
}

public void setHash(String v){
    hash=v;
} 

public String getObjectName(){
    return objectName;
} 

public void setObjectName(String v){
    objectName=v;
} 

public Integer getHttpStatus(){
    return httpStatus;
} 

public void setHttpStatus(Integer v){
    httpStatus=v;
} 

public String getError(){
    return error;
} 

public void setError(String v){
    error=v;
} 

public LocalDateTime getStartedAt(){
    return startedAt;
} 

public LocalDateTime getFinishedAt(){
    return finishedAt;
} 

public void setFinishedAt(LocalDateTime v){
    finishedAt=v;
}

}
