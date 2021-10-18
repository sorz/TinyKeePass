pub(crate) mod kdf;
pub(crate) mod error;

use sha2::{Digest, Sha256, Sha512};
use std::{
    cmp::min,
    io::{self, Read},
    convert::TryInto,
};
use hmac::{Hmac, NewMac, Mac};

pub(crate) const KDF_OUTPUT_LENGTH: usize = 32;
pub(crate) const SHA256_OUTPUT_LENGTH: usize = 32;
pub(crate) const SHA512_OUTPUT_LENGTH: usize = 64;

pub(crate) fn calculate_sha256<R: Read>(input: &mut R, length: usize) -> io::Result<[u8; SHA256_OUTPUT_LENGTH]> {
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64]; // Block size of SHA-256
    let mut pos = 0usize;
    while pos < length {
        let n = min(buf.len(), length - pos);
        input.read_exact(&mut buf[..n])?;
        hasher.update(&buf[..n]);
        pos += n;
    }
    Ok(hasher.finalize().into())
}

pub(crate) fn verify_hmac_sha256<R: Read>(input: &mut R, length: usize, key: HmacKey, tag: &[u8]) -> io::Result<bool> {
    let mut hmac = key.sha256();
    let mut buf = [0u8; 64]; // Block size of SHA-256
    let mut pos = 0usize;
    while pos < length {
        let n = min(buf.len(), length - pos);
        input.read_exact(&mut buf[..n])?;
        hmac.update(&buf[..n]);
        pos += n;
    }
    Ok(hmac.verify(tag).is_ok())
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct MasterSeed([u8; 16]);

#[derive(Debug, Clone, Copy)]
pub(crate) struct TransformSeed([u8; 16]);

#[derive(Debug, Clone)]
pub(crate) struct ProtectedStreamKey(Box<[u8]>);

#[derive(Debug, Clone)]
pub(crate) struct CompositeKey {
    key: [u8; SHA256_OUTPUT_LENGTH],
}

#[derive(Debug, Clone)]
pub(crate) struct TransformedKey {
    key: [u8; KDF_OUTPUT_LENGTH],
}

#[derive(Debug, Clone)]
pub(crate) struct HmacKey {
    key: [u8; SHA512_OUTPUT_LENGTH],
}

impl CompositeKey {
    pub(crate) fn fromPassword<T: AsRef<[u8]>>(password: T) -> Self {
        let hash_pwd = Sha256::digest(password.as_ref());
        let key = Sha256::digest(&hash_pwd);
        Self { key: key.into() }
    }
}


impl MasterSeed {
    pub(crate) fn read_from<R: Read>(reader: &mut R) -> io::Result<Self> {
        let mut buf = [0u8; 16];
        reader.read_exact(&mut buf)?;
        Ok(Self(buf))
    }
}


impl HmacKey {
    pub(crate) fn new(master_seed: &MasterSeed, transformed_key: &TransformedKey) -> Self {
        let mut hasher = Sha512::new();
        hasher.update(&master_seed.0);
        hasher.update(&transformed_key.key);
        hasher.update(&[0x01]);
        Self { key: hasher.finalize().as_slice().try_into().unwrap() }
    }

    pub(crate) fn sha256(&self) -> Hmac<Sha256> {
        Hmac::new(self.key[..].into())
    }

}