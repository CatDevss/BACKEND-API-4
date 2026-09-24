package catdevs.georuraldatahub.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario")
public class User {

    @Id
    @Column(name = "usr_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usr_nome", nullable = false)
    private String name;

    @Column(name = "usr_email", nullable = false, unique = true)
    private String email;

    @Column(name = "usr_senha", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "usr_tipo", nullable = false)
    private UserType userType;

    @Column(name = "usr_status", nullable = false)
    private int userStatus;

    public User() {
    }

    public User(String name, String email, String password, UserType userType, int userStatus) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.userType = userType;
        this.userStatus = userStatus;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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