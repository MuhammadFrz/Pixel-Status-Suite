import os, subprocess, struct, base64, hashlib, zipfile, time
from cryptography import x509
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs7
from apksigcopier import zip_data
from apksigtool import apk_digest_chunked

ADB = r"c:\Users\Muhammad Frz\Downloads\Compressed\platform-tools-latest-windows\platform-tools\adb.exe"
DEV = "192.168.70.158:5555"
SCRATCH = r"C:\Users\Muhammad Frz\.gemini\antigravity-ide\brain\0c5d69d9-5dd3-41d5-b4a4-ff1e7319780e\scratch"
JAVAC = os.path.join(SCRATCH, r"jdk17\jdk-17.0.20.1+1\bin\javac.exe")
JAVA = os.path.join(SCRATCH, r"jdk17\jdk-17.0.20.1+1\bin\java.exe")
R8 = os.path.join(SCRATCH, "r8.jar")
STUBS = os.path.join(SCRATCH, "stubs")
APP_DIR = os.path.join(SCRATCH, "pixel_app")

subprocess.run([ADB, "connect", DEV], capture_output=True)

# 1. Compile Java with javac
print("=== 1. Compiling Java Sources ===")
classes_dir = os.path.join(APP_DIR, "bin", "classes")
os.makedirs(classes_dir, exist_ok=True)

java_files = []
for root, dirs, files in os.walk(os.path.join(APP_DIR, "src")):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

for root, dirs, files in os.walk(STUBS):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

cmd_javac = [JAVAC, "-d", classes_dir] + java_files
res = subprocess.run(cmd_javac, capture_output=True, text=True)
if res.returncode != 0:
    print("JAVAC ERROR:\n", res.stderr)
    exit(1)
print(f"Compiled {len(java_files)} java files successfully!")

# 2. Compile to DEX with D8
print("=== 2. Compiling DEX with D8 ===")
dex_out = os.path.join(APP_DIR, "bin")
suite_classes = []
for root, dirs, files in os.walk(os.path.join(classes_dir, "com", "pixel", "statussuite")):
    for f in files:
        if f.endswith(".class"):
            suite_classes.append(os.path.join(root, f))

cmd_d8 = [JAVA, "-cp", R8, "com.android.tools.r8.D8", "--output", dex_out, "--min-api", "31"] + suite_classes
res_d8 = subprocess.run(cmd_d8, capture_output=True, text=True)
if res_d8.returncode != 0:
    print("D8 ERROR:\n", res_d8.stderr)
    exit(1)

dex_file = os.path.join(dex_out, "classes.dex")
print(f"classes.dex generated: {os.path.getsize(dex_file)} bytes")

# 3. Package Resources and Assets on Device with aapt2
print("=== 3. Packaging Resources with aapt2 on Device ===")
remote_build = "/data/local/tmp/suite_build"
subprocess.run([ADB, "-s", DEV, "shell", f"rm -rf {remote_build} && mkdir -p {remote_build}/res {remote_build}/assets"], capture_output=True)

# Push AndroidManifest.xml, res, and assets
# Clean any .metadata.json in res
for root, dirs, files in os.walk(os.path.join(APP_DIR, "res")):
    for f in files:
        if f.endswith(".metadata.json"):
            os.remove(os.path.join(root, f))

subprocess.run([ADB, "-s", DEV, "push", os.path.join(APP_DIR, "AndroidManifest.xml"), f"{remote_build}/AndroidManifest.xml"], capture_output=True)
subprocess.run([ADB, "-s", DEV, "push", os.path.join(APP_DIR, "res"), f"{remote_build}/"], capture_output=True)
subprocess.run([ADB, "-s", DEV, "push", os.path.join(APP_DIR, "assets"), f"{remote_build}/"], capture_output=True)
subprocess.run([ADB, "-s", DEV, "shell", f"rm -f {remote_build}/res/*/*.metadata.json"], capture_output=True)

# Run aapt2 compile and link on phone
aapt2_script = f"""#!/system/bin/sh
set -e
AAPT2="/data/local/tmp/bin/aapt2"
FRAMEWORK="/system/framework/framework-res.apk"
TMP="{remote_build}"

"$AAPT2" compile --dir "$TMP/res" -o "$TMP/res.zip"
"$AAPT2" link --min-sdk-version 31 --target-sdk-version 34 -o "$TMP/base.apk" -I "$FRAMEWORK" --manifest "$TMP/AndroidManifest.xml" -A "$TMP/assets" "$TMP/res.zip"
chmod 666 "$TMP/base.apk"
"""
subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", f"cat << 'EOF' > /data/local/tmp/build_suite_apk.sh\n{aapt2_script}\nEOF\nchmod 755 /data/local/tmp/build_suite_apk.sh"])
res_aapt2 = subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", "/data/local/tmp/build_suite_apk.sh"], capture_output=True, text=True)
print("aapt2 link output:", res_aapt2.stdout, res_aapt2.stderr)

# Pull base.apk
local_base = os.path.join(SCRATCH, "suite_base.apk")
subprocess.run([ADB, "-s", DEV, "pull", f"{remote_build}/base.apk", local_base], capture_output=True)
print(f"base.apk pulled: {os.path.getsize(local_base)} bytes")

# 4. Add classes.dex to base.apk
local_unaligned = os.path.join(SCRATCH, "suite_unaligned.apk")
with zipfile.ZipFile(local_base, "r") as zin:
    with zipfile.ZipFile(local_unaligned, "w") as zout:
        for item in zin.infolist():
            zout.writestr(item, zin.read(item.filename))
        with open(dex_file, "rb") as df:
            zout.writestr("classes.dex", df.read())

print(f"classes.dex packaged into APK: {os.path.getsize(local_unaligned)} bytes")

# 5. Align and Sign APK (v1 + v2) with LineageOS platform key
print("=== 4. Signing APK with LineageOS Platform Key ===")
with open(f"{SCRATCH}/platform.x509.pem", "rb") as f:
    cert = x509.load_pem_x509_certificate(f.read())
cert_der = cert.public_bytes(serialization.Encoding.DER)

with open(f"{SCRATCH}/platform.pk8", "rb") as f:
    private_key = serialization.load_der_private_key(f.read(), password=None)

pubkey_der = cert.public_key().public_bytes(
    serialization.Encoding.DER,
    serialization.PublicFormat.SubjectPublicKeyInfo
)

def lp(data):
    return struct.pack("<I", len(data)) + data

def sign_v1_and_align(in_apk, out_apk):
    with zipfile.ZipFile(in_apk, "r") as zin:
        entries = {}
        for info in zin.infolist():
            if not info.filename.startswith("META-INF/"):
                entries[info.filename] = zin.read(info.filename)

    manifest_lines = [
        "Manifest-Version: 1.0\r\n",
        "Built-By: Android\r\n",
        "Created-By: Android\r\n",
        "\r\n"
    ]
    entry_sections = {}
    for k in sorted(entries.keys()):
        h = base64.b64encode(hashlib.sha256(entries[k]).digest()).decode()
        sec = f"Name: {k}\r\nSHA-256-Digest: {h}\r\n\r\n"
        manifest_lines.append(sec)
        entry_sections[k] = sec.encode("utf-8")
    manifest_bytes = "".join(manifest_lines).encode("utf-8")

    sf_lines = [
        "Signature-Version: 1.0\r\n",
        "Created-By: Android\r\n",
        f"SHA-256-Digest-Manifest: {base64.b64encode(hashlib.sha256(manifest_bytes).digest()).decode()}\r\n",
        "\r\n"
    ]
    for k in sorted(entries.keys()):
        h_sec = base64.b64encode(hashlib.sha256(entry_sections[k]).digest()).decode()
        sf_lines.append(f"Name: {k}\r\nSHA-256-Digest: {h_sec}\r\n\r\n")
    sf_bytes = "".join(sf_lines).encode("utf-8")

    builder = pkcs7.PKCS7SignatureBuilder().set_data(sf_bytes).add_signer(
        cert, private_key, hashes.SHA256()
    )
    rsa_bytes = builder.sign(serialization.Encoding.DER, options=[pkcs7.PKCS7Options.DetachedSignature])

    all_files = {}
    all_files["META-INF/MANIFEST.MF"] = (manifest_bytes, zipfile.ZIP_DEFLATED)
    all_files["META-INF/CERT.SF"] = (sf_bytes, zipfile.ZIP_DEFLATED)
    all_files["META-INF/CERT.RSA"] = (rsa_bytes, zipfile.ZIP_DEFLATED)
    
    for k in sorted(entries.keys()):
        if k.endswith(".png") or k.endswith(".arsc"):
            all_files[k] = (entries[k], zipfile.ZIP_STORED)
        else:
            all_files[k] = (entries[k], zipfile.ZIP_DEFLATED)

    with zipfile.ZipFile(out_apk, "w") as zout:
        for fname, (data, comp) in all_files.items():
            zinfo = zipfile.ZipInfo(fname)
            zinfo.compress_type = comp
            zinfo.date_time = (2026, 1, 1, 0, 0, 0)
            if comp == zipfile.ZIP_STORED:
                cur_pos = zout.fp.tell()
                f_len = len(fname.encode("utf-8"))
                padding_needed = (4 - ((cur_pos + 30 + f_len) % 4)) % 4
                zinfo.extra = b"\x00" * padding_needed
            zout.writestr(zinfo, data)

def sign_v2(in_apk, out_apk):
    cd_offset, eocd_offset, _ = zip_data(in_apk)
    apk_digest = apk_digest_chunked(in_apk, cd_offset, hashlib.sha256)
    digest_entry = struct.pack("<I", 0x0103) + lp(apk_digest)
    digests_block = lp(lp(digest_entry))
    certs_block = lp(lp(cert_der))
    attrs_block = lp(b"")
    signed_data = digests_block + certs_block + attrs_block
    
    sig_bytes = private_key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    sig_entry = struct.pack("<I", 0x0103) + lp(sig_bytes)
    signatures_block = lp(lp(sig_entry))
    pubkey_block = lp(pubkey_der)
    signer_data = lp(signed_data) + signatures_block + pubkey_block
    v2_value = lp(lp(signer_data))
    
    pair_id = 0x7109871a
    pair_len = 4 + len(v2_value)
    pair = struct.pack("<QL", pair_len, pair_id) + v2_value
    
    sb_size = len(pair) + 8 + 16
    signing_block = struct.pack("<Q", sb_size) + pair + struct.pack("<Q", sb_size) + b"APK Sig Block 42"
    
    with open(in_apk, "rb") as f:
        sec1 = f.read(cd_offset)
        cd_data = f.read(eocd_offset - cd_offset)
        eocd_data = f.read()
    
    new_cd_offset = cd_offset + len(signing_block)
    new_eocd = eocd_data[:16] + struct.pack("<I", new_cd_offset) + eocd_data[20:]
    
    with open(out_apk, "wb") as f:
        f.write(sec1)
        f.write(signing_block)
        f.write(cd_data)
        f.write(new_eocd)

v1_apk = os.path.join(SCRATCH, "suite_v1.apk")
final_apk = os.path.join(SCRATCH, "PixelStatusSuite.apk")
sign_v1_and_align(local_unaligned, v1_apk)
sign_v2(v1_apk, final_apk)
print(f"PixelStatusSuite.apk successfully signed: {os.path.getsize(final_apk)} bytes")

# 6. Install to Device
print("=== 5. Installing to Device ===")
remote_apk = "/data/local/tmp/PixelStatusSuite.apk"
subprocess.run([ADB, "-s", DEV, "push", final_apk, remote_apk], capture_output=True)
inst_res = subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", f"pm install -r {remote_apk}"], capture_output=True, text=True)
print("PM INSTALL RESULT:", inst_res.stdout.strip(), inst_res.stderr.strip())

# Copy to Magisk module system/priv-app
module_priv_app = "/data/adb/modules/pixel_status_icons/system/priv-app/PixelStatusSuite"
subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", f"mkdir -p {module_priv_app} && cp -f {remote_apk} {module_priv_app}/PixelStatusSuite.apk && chmod 644 {module_priv_app}/PixelStatusSuite.apk"], capture_output=True)

# 7. Push apply_status_config.sh
print("=== 6. Deploying Runtime Helper Script ===")
local_sh = os.path.join(SCRATCH, "apply_status_config.sh")
subprocess.run([ADB, "-s", DEV, "push", local_sh, "/data/local/tmp/apply_status_config.sh"], capture_output=True)
subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", "chmod 755 /data/local/tmp/apply_status_config.sh && cp -f /data/local/tmp/apply_status_config.sh /data/adb/modules/pixel_status_icons/apply_status_config.sh"], capture_output=True)

# 8. Restart SystemUI to load updated hook
print("=== 7. Restarting SystemUI ===")
subprocess.run([ADB, "-s", DEV, "shell", "su", "-c", "pkill -f com.android.systemui"], capture_output=True)

# 9. Launch PixelStatusSuite Activity
print("=== 8. Launching Pixel Status Suite App ===")
subprocess.run([ADB, "-s", DEV, "shell", "am start -n com.pixel.statussuite/.MainActivity"], capture_output=True)
time.sleep(2)

# 10. Capture screenshot of the App
screen_path = os.path.join(SCRATCH, "suite_app_screen.png")
subprocess.run([ADB, "-s", DEV, "shell", "screencap -p /sdcard/suite_app_screen.png"], capture_output=True)
subprocess.run([ADB, "-s", DEV, "pull", "/sdcard/suite_app_screen.png", screen_path], capture_output=True)
print("Complete! Screenshot saved to suite_app_screen.png")
