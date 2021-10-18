use byteorder::{ReadBytesExt, LE};
use std::{collections::HashMap, convert::TryInto, io::Read};

use crate::error::BinaryParseError;

#[derive(Debug, Clone)]
pub(crate) enum Value {
    UInt32(u32),
    UInt64(u64),
    Bool(bool),
    Int32(i32),
    Int64(i64),
    String(String),
    Bytes(Box<[u8]>),
}

impl Value {
    fn parse<R: Read>(
        value_type: u8,
        length: u32,
        reader: &mut R,
    ) -> Result<Option<Self>, BinaryParseError> {
        let v = match value_type {
            0x04 => Self::UInt32(reader.read_u32::<LE>()?),
            0x05 => Self::UInt64(reader.read_u64::<LE>()?),
            0x08 => Self::Bool(reader.read_u8()? > 0),
            0x0C => Self::Int32(reader.read_i32::<LE>()?),
            0x0D => Self::Int64(reader.read_i64::<LE>()?),
            _ => {
                // String | Bytes
                let mut buf = vec![0u8; length.try_into().unwrap()];
                reader.read_exact(&mut buf[..])?;
                match value_type {
                    0x18 => Self::String(String::from_utf8(buf)?),
                    0x42 => Self::Bytes(buf.into()),
                    _ => return Ok(None), // Ignore unknown type
                }
            }
        };
        Ok(Some(v))
    }
}

#[derive(Debug)]
pub(crate) struct VariantDictionary {
    entries: HashMap<String, Value>,
}

impl VariantDictionary {
    pub(crate) fn parse<R: Read>(mut reader: R) -> Result<Self, BinaryParseError> {
        // Check version
        let version = reader.read_u16::<LE>()?;
        if version & 0xff00 != 0x0100 {
            return Err(BinaryParseError::UnsupportedVariantDictionaryVersion(
                version,
            ));
        }

        // Entries
        let mut entries = HashMap::new();
        loop {
            let value_type = reader.read_u8()?;
            // Check terminator byte
            if value_type == 0x00 {
                break;
            }
            // Parsing key name
            let key = {
                let length = reader.read_u32::<LE>()?;
                if length > 1024 * 1024 {
                    // 1 MiB
                    return Err(BinaryParseError::FieldTooLarge(length));
                }
                let mut buf = vec![0u8; length.try_into().unwrap()];
                reader.read_exact(&mut buf)?;
                String::from_utf8(buf)?
            };
            // Parsing values
            let value = {
                let length = reader.read_u32::<LE>()?;
                if length > 8 * 1024 * 1024 {
                    // 8 MiB
                    return Err(BinaryParseError::FieldTooLarge(length));
                }
                Value::parse(value_type, length, &mut reader)?
            };
            if let Some(value) = value {
                entries.insert(key, value);
            }
        }

        Ok(Self { entries })
    }

    pub(crate) fn get_u32(&self, key: &str) -> Option<u32> {
        match self.entries.get(key) {
            Some(Value::UInt32(v)) => Some(*v),
            _ => None,
        }
    }

    pub(crate) fn get_u64(&self, key: &str) -> Option<u64> {
        match self.entries.get(key) {
            Some(Value::UInt32(v)) => Some((*v).into()),
            Some(Value::UInt64(v)) => Some(*v),
            _ => None,
        }
    }

    pub(crate) fn get_bytes<'a>(&'a self, key: &str) -> Option<&'a [u8]> {
        match self.entries.get(key) {
            Some(Value::Bytes(v)) => Some(v),
            Some(Value::String(v)) => Some(v.as_bytes()),
            _ => None,
        }
    }
}
