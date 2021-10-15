use std::io::{self, Read};

#[derive(Debug, Clone, Copy)]
pub(crate) struct MasterSeed([u8; 16]);

#[derive(Debug, Clone, Copy)]
pub(crate) struct TransformSeed([u8; 16]);

#[derive(Debug, Clone)]
pub(crate) struct ProtectedStreamKey(Box<[u8]>);

impl MasterSeed {
    pub(crate) fn read_from<R: Read>(reader: &mut R) -> io::Result<Self> {
        let mut buf = [0u8; 16];
        reader.read(&mut buf)?;
        Ok(Self(buf))
    }
}
