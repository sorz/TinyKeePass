use thiserror::Error;

#[derive(Error, Debug)]
pub enum BinaryParseError {
    #[error("not a KDB(X) database (magic {magic:#x} mismatched)")]
    NotKdbFile { magic: u32 },

    #[error("unsupported database version (magic {magic:#x}, version {version:#x})")]
    UnsupportedVersion { magic: u32, version: u32 },

    #[error("unsupported VariantDictionary version ({0:#x})")]
    UnsupportedVariantDictionaryVersion(u16),

    #[error("data store disconnected")]
    UnknownFieldValue { field: &'static str, value: String },

    #[error("field too large ({0} bytes)")]
    FieldTooLarge(u32),

    #[error("missing required field `{0}`")]
    FieldNotFound(&'static str),

    #[error("header corruppted")]
    ValidationError,

    #[error(transparent)]
    IOError(#[from] std::io::Error),

    #[error(transparent)]
    Utf8Error(#[from] std::string::FromUtf8Error),
}
