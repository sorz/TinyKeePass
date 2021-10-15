use byteorder::{ReadBytesExt, LE};
use std::{
    convert::{TryFrom, TryInto},
    io::Read,
};

use crate::binary_parser::{
    crypto::{MasterSeed, Sha256ReadStream, SHA256_LENGTH},
    dict::VariantDictionary,
    error::BinaryParseError,
    parse_header_field,
};

const FILE_MAGIC: u32 = 0x9AA2D903;
const FILE_MAGIC_KDBX: u32 = 0xB54BFB67;

const COMPRESSION_METHOD_NONE: u32 = 0;
const COMPRESSION_METHOD_GZIP: u32 = 1;

const OUTER_CIPHER_AES128: u128 = 0x35ddf83d563a748dc3416494a105ab61;
const OUTER_CIPHER_AES256: u128 = 0xff5afc6a210558be504371bfe6f2c131;
const OUTER_CIPHER_TWOFISH: u128 = 0x6c3465f97ad46aa3b94b6f579ff268ad;
const OUTER_CIPHER_CHACHA20: u128 = 0x9ab5db319a3324a5b54c6f8b2b8a03d6;

const FIELD_CIPHER: u8 = 0x02;
const FIELD_COMPRESSION_METHOD: u8 = 0x03;
const FIELD_MASTER_SEED: u8 = 0x04;
const FIELD_ENCRYPTION_IV: u8 = 0x07;
const FIELD_KDF_PARAMETERS: u8 = 0x0b;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum OuterCipher {
    AES128,
    AES256,
    TwoFish,
    ChaCha20,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CompressionMethod {
    None,
    Gzip,
}

impl TryFrom<u32> for CompressionMethod {
    type Error = BinaryParseError;
    fn try_from(value: u32) -> Result<Self, Self::Error> {
        match value {
            COMPRESSION_METHOD_NONE => Ok(CompressionMethod::None),
            COMPRESSION_METHOD_GZIP => Ok(CompressionMethod::Gzip),
            _ => Err(BinaryParseError::UnknownFieldValue {
                field: "CompressionMethod",
                value: value.to_string(),
            }),
        }
    }
}

impl TryFrom<u128> for OuterCipher {
    type Error = BinaryParseError;
    fn try_from(value: u128) -> Result<Self, Self::Error> {
        match value {
            OUTER_CIPHER_AES128 => Ok(OuterCipher::AES128),
            OUTER_CIPHER_AES256 => Ok(OuterCipher::AES256),
            OUTER_CIPHER_TWOFISH => Ok(OuterCipher::TwoFish),
            OUTER_CIPHER_CHACHA20 => Ok(OuterCipher::ChaCha20),
            _ => Err(BinaryParseError::UnknownFieldValue {
                field: "OuterCipher",
                value: format!("{:?}", value),
            }),
        }
    }
}

#[derive(Debug)]
pub(crate) struct OuterHeader {
    cipher: OuterCipher,
    compression: CompressionMethod,
    master_seed: MasterSeed,
    kdf_paramters: VariantDictionary,
}

impl OuterHeader {
    pub(crate) fn parse<R: Read>(reader: R) -> Result<(R, Self), BinaryParseError> {
        let mut reader = Sha256ReadStream::new(reader);

        // Parsing file magic & datbase version
        let magic = reader.read_u32::<LE>()?;
        if magic != FILE_MAGIC {
            return Err(BinaryParseError::NotKdbFile { magic });
        }
        let magic = reader.read_u32::<LE>()?;
        let version = reader.read_u32::<LE>()?;
        if magic != FILE_MAGIC_KDBX || version & 0xffff0000 != 0x0004_0000 {
            return Err(BinaryParseError::UnsupportedVersion { magic, version });
        }

        // Parse headers
        let mut cipher: Option<OuterCipher> = None;
        let mut compression: Option<CompressionMethod> = None;
        let mut master_seed: Option<MasterSeed> = None;
        let mut kdf_paramters: Option<VariantDictionary> = None;
        let mut buf = vec![];
        while let Some((field_type, length)) = parse_header_field(&mut reader)? {
            if length > 8 * 1024 * 1024 {
                // 8 MiB
                return Err(BinaryParseError::FieldTooLarge(length));
            }
            buf.resize(length.try_into().unwrap(), 0);
            reader.read_exact(&mut buf)?;
            let mut value = buf.as_slice();
            match field_type {
                FIELD_CIPHER => {
                    cipher.replace(value.read_u128::<LE>()?.try_into()?);
                }
                FIELD_COMPRESSION_METHOD => {
                    compression.replace(value.read_u32::<LE>()?.try_into()?);
                }
                FIELD_MASTER_SEED => {
                    master_seed.replace(MasterSeed::read_from(&mut value)?);
                }
                FIELD_KDF_PARAMETERS => {
                    kdf_paramters.replace(VariantDictionary::parse(buf.as_slice())?);
                }
                _ => (),
            }
        }
        let header = Self {
            cipher: cipher.ok_or(BinaryParseError::FieldNotFound("Cipher"))?,
            compression: compression.ok_or(BinaryParseError::FieldNotFound("CompressionMethod"))?,
            master_seed: master_seed.ok_or(BinaryParseError::FieldNotFound("MasterSeed"))?,
            kdf_paramters: kdf_paramters.ok_or(BinaryParseError::FieldNotFound("KdfParamters"))?,
        };

        // Check header hash
        let (mut reader, hash_actual) = reader.finalize();
        let mut hash = [0u8; SHA256_LENGTH];
        reader.read_exact(&mut hash)?;
        if hash != hash_actual {
            return Err(BinaryParseError::ValidationError);
        }

        Ok((reader, header))
    }
}

#[test]
fn test_parse_outer_header() {
    use std::fs::File;

    let f = File::open("test_data/kdbx4_chacha20_argon2.kdbx").unwrap();
    let (_f, header) = OuterHeader::parse(f).unwrap();
    assert_eq!(OuterCipher::ChaCha20, header.cipher);
    assert_eq!(CompressionMethod::Gzip, header.compression);
}
