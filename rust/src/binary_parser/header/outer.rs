use byteorder::{ReadBytesExt, LE};
use std::{convert::{TryFrom, TryInto}, io::{Read, Seek, SeekFrom}, slice::SliceIndex};
use argon2::Variant as Argon2Variant;

use crate::{
    crypto::{MasterSeed, calculate_sha256, verify_hmac_sha256, SHA256_OUTPUT_LENGTH, CompositeKey, HmacKey, kdf::{Kdf, AesKdf, Argon2}},
    error::BinaryParseError,
    binary_parser::{dict::VariantDictionary, parse_header_field},
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

const KDF_AES: u128 = 0x388264004a117d92c04aa77982bb027c;
const KDF_ARGON2_D: u128 = 0xc0ae303a4a9f7914b44298cdf6d63ef;


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
enum KdfMethod {
    Aes(AesKdf),
    Argon2(Argon2),
}

impl TryFrom<VariantDictionary> for KdfMethod {
    type Error = BinaryParseError;

    fn try_from(dict: VariantDictionary) -> Result<Self, Self::Error> {
        let mut uuid = dict.get_bytes("$UUID").ok_or(BinaryParseError::FieldNotFound("KDF.$UUID"))?;
        match uuid.read_u128::<LE>()? {
            KDF_AES => unimplemented!(),
            KDF_ARGON2_D => Ok(Self::Argon2(Argon2 {
                variant: Argon2Variant::Argon2d,
                version: dict.get_u32("V").ok_or(BinaryParseError::FieldNotFound("KDF.V"))?,
                salt: dict.get_bytes("S").ok_or(BinaryParseError::FieldNotFound("KDF.S"))?.into(),
                time_cost: dict.get_u64("I")
                    .ok_or(BinaryParseError::FieldNotFound("KDF.I"))?
                    .try_into()
                    .map_err(|_| BinaryParseError::UnknownFieldValue { field: "KDF.I", value: "too large".into() })?,
                mem_cost: {
                    let bytes = dict.get_u64("M").ok_or(BinaryParseError::FieldNotFound("KDF.M"))?;
                    (bytes / 1024)
                        .try_into()
                        .map_err(|_| BinaryParseError::UnknownFieldValue { field: "KDF.M", value: "too large".into() })?
                },
                lanes: dict.get_u32("P").ok_or(BinaryParseError::FieldNotFound("KDF.P"))?,
            })),
            _ => Err(BinaryParseError::UnknownFieldValue { field: "$UUID", value: format!("{:?}", uuid) }),
        }
    }
}


#[derive(Debug)]
pub(crate) struct OuterHeader {
    header_length: usize,
    body_start_at: usize,
    cipher: OuterCipher,
    compression: CompressionMethod,
    master_seed: MasterSeed,
    kdf_paramters: VariantDictionary,
    hmac_tag: [u8; SHA256_OUTPUT_LENGTH],
}

impl OuterHeader {
    pub(crate) fn parse<R: Read + Seek>(reader: &mut R) -> Result<Self, BinaryParseError> {

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
        while let Some((field_type, length)) = parse_header_field(reader)? {
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
        let header_length: usize = reader.stream_position()?.try_into().unwrap();

        // Check header hash
        reader.seek(SeekFrom::Start(0))?;
        let hash_actual = calculate_sha256(reader, header_length)?;
        let hash_record = {
            let mut hash = [0u8; SHA256_OUTPUT_LENGTH];
            reader.read_exact(&mut hash)?;
            hash
        };
        if hash_record != hash_actual {
            return Err(BinaryParseError::ValidationError);
        }

        // Read HMAC tag
        let hmac_tag = {
            let mut hmac = [0u8; SHA256_OUTPUT_LENGTH];
            reader.read_exact(&mut hmac)?;
            hmac
        };
        let body_start_at: usize = reader.stream_position()?.try_into().unwrap();

        let header = Self {
            header_length, body_start_at, hmac_tag,
            cipher: cipher.ok_or(BinaryParseError::FieldNotFound("Cipher"))?,
            compression: compression.ok_or(BinaryParseError::FieldNotFound("CompressionMethod"))?,
            master_seed: master_seed.ok_or(BinaryParseError::FieldNotFound("MasterSeed"))?,
            kdf_paramters: kdf_paramters.ok_or(BinaryParseError::FieldNotFound("KdfParamters"))?,
        };
        Ok(header)
    }

    fn validate<R: Read + Seek>(self, reader: &mut R, composite_key: CompositeKey) -> Result<ValidatedOuterHeader, BinaryParseError>  {

        let kdf: KdfMethod = self.kdf_paramters.try_into()?;
        println!("kdf {:?}", kdf);
        let transformed_key = match kdf {
            KdfMethod::Aes(m) => m.derive(&composite_key),
            KdfMethod::Argon2(m) => m.derive(&composite_key),
        }?;


        let hmac_key = HmacKey::new(&self.master_seed, &transformed_key);
        reader.seek(SeekFrom::Start(0))?;
        let verified = verify_hmac_sha256(reader, self.header_length, hmac_key, &self.hmac_tag)?;
        if !verified {
            return Err(BinaryParseError::AuthenticationError);
        }


        unimplemented!()
    }
}


#[derive(Debug)]
pub(crate) struct ValidatedOuterHeader {
    body_start_at: usize,
    cipher: OuterCipher,
    compression: CompressionMethod,
    master_seed: MasterSeed,
}


#[test]
fn test_parse_outer_header() {
    use std::fs::File;

    let mut f = File::open("test_data/kdbx4_chacha20_argon2.kdbx").unwrap();
    let header= OuterHeader::parse(&mut f).unwrap();
    assert_eq!(OuterCipher::ChaCha20, header.cipher);
    assert_eq!(CompressionMethod::Gzip, header.compression);

    println!("{:?}", header.kdf_paramters);
    let key = CompositeKey::fromPassword("password");
    let header = header.validate(&mut f, key).unwrap();

}
