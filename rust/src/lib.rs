use jni::{
    JNIEnv,
    objects::{JClass, JObject, JValue, JString},
    sys::{jbyteArray, jint, jlong, jstring},
};

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_sorz_lab_tinykeepass_keepass_KdbxNative_loadDatabase(
    env: JNIEnv,
    _class: JClass,
    path: JString,
    password: JString,
) -> jbyteArray {
    let path = env.get_string(path)
        .expect("fail to get String of path");
    let password = env.get_string(password)
        .expect("fail to get String of password");

    println!("Open database {}", path.to_string_lossy());

    env.byte_array_from_slice(b"Hello, world!~").unwrap()
}