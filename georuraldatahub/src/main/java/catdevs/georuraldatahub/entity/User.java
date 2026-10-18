package catdevs.georuraldatahub.entity;

import catdevs.georuraldatahub.entity.enums.UserType;
import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class User {

    @Id
    @Column(name = "usr_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usr_nome")
    private String name;

    @Column(name = "usr_email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "usr_tipo")
    private UserType userType;

    @Column(name = "usr_status")
    private int userStatus;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public int getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(int userStatus) {
        this.userStatus = userStatus;
    }   
}