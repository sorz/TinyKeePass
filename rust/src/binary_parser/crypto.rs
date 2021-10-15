use sha2::{Digest, Sha256};
use std::io::{self, Read};

pub(crate) const SHA256_LENGTH: usize = 32;

pub(crate) struct Sha256ReadStream<R: Read> {
    reader: R,
    hasher: Sha256,
}

impl<R: Read> Sha256ReadStream<R> {
    pub(crate) fn new(reader: R) -> Self {
        Self {
            reader,
            hasher: Sha256::new(),
        }
    }

    pub(crate) fn finalize(self) -> (R, [u8; SHA256_LENGTH]) {
        (self.reader, self.hasher.finalize().into())
    }
}
impl<R: Read> Read for Sha256ReadStream<R> {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let n = self.reader.read(buf)?;
        self.hasher.update(&buf[..n]);
        Ok(n)
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct MasterSeed([u8; 16]);

#[derive(Debug, Clone, Copy)]
pub(crate) struct TransformSeed([u8; 16]);

#[derive(Debug, Clone)]
pub(crate) struct ProtectedStreamKey(Box<[u8]>);

impl MasterSeed {
    pub(crate) fn read_from<R: Read>(reader: &mut R) -> io::Result<Self> {
        let mut buf = [0u8; 16];
        reader.read_exact(&mut buf)?;
        Ok(Self(buf))
    }
}
