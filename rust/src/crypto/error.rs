use thiserror::Error;

#[derive(Error, Debug)]
pub enum CryptoError {
    #[error(transparent)]
    Argon2Error(#[from] argon2::Error),
}