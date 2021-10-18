use std::convert::TryInto;

use super::{CompositeKey, TransformedKey, error::CryptoError, KDF_OUTPUT_LENGTH};


pub(crate) trait Kdf {
    fn derive(&self, composite_key: &CompositeKey) -> Result<TransformedKey, CryptoError>;
}

#[derive(Debug)]
pub(crate) struct AesKdf {
    seed: Box<[u8]>,
    iterations: usize,
}

#[derive(Debug)]
pub(crate) struct Argon2 {
    pub(crate) variant: argon2::Variant,
    pub(crate) version: u32,
    pub(crate) salt: Box<[u8]>,
    pub(crate) time_cost: u32,
    pub(crate) mem_cost: u32,
    pub(crate) lanes: u32,
}

impl Kdf for AesKdf {
    fn derive(&self, composite_key: &CompositeKey) -> Result<TransformedKey, CryptoError> {
        unimplemented!()
    }
}

impl Kdf for Argon2 {
    fn derive(&self, composite_key: &CompositeKey) -> Result<TransformedKey, CryptoError> {
        let version = argon2::Version::from_u32(self.version)?;

        let config = argon2::Config {
            variant: self.variant,
            version: version,
            mem_cost: self.mem_cost,
            time_cost: self.time_cost,
            lanes: self.lanes,
            thread_mode: argon2::ThreadMode::Parallel,
            secret: &[],
            ad: &[],
            hash_length: KDF_OUTPUT_LENGTH as u32,
        };

        let hash = argon2::hash_raw(&composite_key.key, &self.salt, &config)?;
        let key: [u8; KDF_OUTPUT_LENGTH] = hash.try_into().unwrap();
        Ok(TransformedKey {
            key
        })
    }
}