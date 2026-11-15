-- MODELO: credencial pessoal para acesso do Oracle ao OCI Object Storage.
-- credential_name e um nome interno do banco, nao o OCID do bucket.
-- username: usuario OCI exato; incluir dominio quando necessario.
-- password: Auth Token gerado pelo mesmo usuario, nao a senha de login.
-- Se a credencial ja existir, confira seu usuario antes de repetir CREATE_CREDENTIAL.
SET DEFINE OFF

BEGIN
    DBMS_CLOUD.CREATE_CREDENTIAL(
        credential_name => 'GEORURAL_BUCKET_CRED',
        username        => '<SEU_USUARIO_OCI>',
        password        => '<SEU_AUTH_TOKEN>'
    );
END;
/
