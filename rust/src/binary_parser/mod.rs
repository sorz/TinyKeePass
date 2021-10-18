mod header;
mod dict;

use byteorder::{ReadBytesExt, LE};
use std::{
    convert::TryInto,
    io::{Error, Read},
};

const FIELD_TYPE_EOF: u8 = 0;

fn parse_header_field<R: Read>(reader: &mut R) -> Result<Option<(u8, u32)>, Error> {
    let field_type = reader.read_u8()?;
    let length = reader.read_u32::<LE>()?;
    if field_type == FIELD_TYPE_EOF {
        reader
            .bytes()
            .take(length.try_into().unwrap())
            .find_map(|r| r.err())
            .map_or(Ok(()), Err)?;
        Ok(None)
    } else {
        Ok(Some((field_type, length)))
    }
}
