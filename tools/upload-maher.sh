#!/usr/bin/env bash
# ينزّل تلاوة ماهر المعيقلي لكل آيات القرآن (6236 آية)، يتحقق من كل ملف،
# ثم يرفعها إلى Cloudflare R2. تشغيله على حاسوبك أنت، والمفاتيح تُدخل في الطرفية فقط.
#
# المتطلبات (مرة واحدة): brew install awscli ffmpeg
# التشغيل:
#   export R2_ACCOUNT_ID="معرّف الحساب"
#   export AWS_ACCESS_KEY_ID="مفتاح الوصول"
#   export AWS_SECRET_ACCESS_KEY="المفتاح السري"
#   bash upload-maher.sh

set -u

FOLDER="MaherAlMuaiqly128kbps"
BUCKET="${R2_BUCKET:-quran-audio}"
DIR="$HOME/quran-audio/$FOLDER"
ENDPOINT="https://${R2_ACCOUNT_ID:?ضع R2_ACCOUNT_ID أولاً}.r2.cloudflarestorage.com"
export AWS_DEFAULT_REGION=auto

COUNTS=(7 286 200 176 120 165 206 75 129 109 123 111 43 52 99 128 111 110 98 135 112 78 118 64 77 227 93 88 69 60 34 30 73 54 45 83 182 88 75 85 54 53 89 59 37 35 38 29 18 45 60 49 62 55 78 96 29 22 24 13 14 11 11 18 12 12 30 52 52 44 28 28 20 56 40 31 50 40 46 42 29 19 36 25 22 17 19 26 30 20 15 21 11 8 8 19 5 8 8 11 11 8 3 9 5 4 7 3 6 3 5 4 5 6)

mkdir -p "$DIR"
FAILED="$DIR/failed.txt"
: > "$FAILED"

total=0; ok=0; bad=0
for s in $(seq 1 114); do
  n=${COUNTS[$((s-1))]}
  for a in $(seq 1 "$n"); do
    total=$((total+1))
    name=$(printf "%03d%03d.mp3" "$s" "$a")
    file="$DIR/$name"
    # نزّل فقط إن لم يكن الملف موجوداً وسليماً
    if [ ! -s "$file" ]; then
      curl -sS --fail --retry 4 --retry-delay 2 -o "$file" \
        "https://everyayah.com/data/$FOLDER/$name" 2>/dev/null || { rm -f "$file"; }
    fi
    # تحقق: الملف موجود، وله مدة صوت صحيحة
    dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$file" 2>/dev/null)
    if [ -s "$file" ] && [ -n "$dur" ] && awk "BEGIN{exit !($dur > 0.2)}"; then
      ok=$((ok+1))
    else
      bad=$((bad+1)); rm -f "$file"; echo "$s:$a" >> "$FAILED"
    fi
  done
  echo "السورة $s — مكتمل حتى الآن: $ok / $total، أخطاء: $bad"
done

echo "=== انتهى التنزيل: سليم $ok، معطّل $bad من $total ==="
echo "الآيات المعطّلة محفوظة في: $FAILED"

if [ "$ok" -eq 0 ]; then echo "لا يوجد ملف سليم، توقفت."; exit 1; fi

echo "=== الرفع إلى R2 ==="
aws s3 sync "$DIR" "s3://$BUCKET/$FOLDER" \
  --endpoint-url "$ENDPOINT" --exclude "failed.txt" --content-type audio/mpeg

echo "تم. المسار في R2: $BUCKET/$FOLDER"
