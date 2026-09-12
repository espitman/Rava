# آزمون روی گوشی واقعی

> **Legacy reference:** این سند مربوط به معماری قدیمی Chromium/HTTP است و نباید
> برای نسخهٔ 1.0 اجرا شود. راهنمای نصب جاری در `README.md` است.

این پروژه از emulator استفاده نمی‌کند. آزمون Android و Termux روی یک گوشی واقعی انجام می‌شود.

## آماده‌سازی اتصال

1. Developer options و USB debugging را روی گوشی فعال کنید.
2. گوشی را با USB وصل و پیام اعتماد به کامپیوتر را تأیید کنید.
3. از ریشهٔ پروژه اجرا کنید:

```bash
bash scripts/android-preflight.sh
```

## پیش‌نیاز Termux

نسخهٔ Play Store قدیمی است. نسخهٔ جاری Termux باید از F-Droid یا انتشار رسمی GitHub نصب شود. نصب packageها و اجرای موتور بعد از ثبت نتیجهٔ preflight انجام می‌شود، چون ABI و نسخهٔ Android روی سازگاری `curl-cffi` و Chromium اثر دارند.

## دروازهٔ عبور فاز Android

- Python 3.11 یا جدیدتر داخل Termux اجرا شود.
- wheel یا build موفق `curl-cffi` و `orjson` روی ABI واقعی ثبت شود. روی ARM64 فعلی، `curl-cffi` wheel اندروید دارد و `orjson` با Rust رسمی Termux build می‌شود.
- `GeminiClient.init()` با نشست شخصی موفق شود و مدل‌های واقعی حساب را برگرداند.
- Chromium با CDP اجرا و ChatGPT-Web2API به آن متصل شود.
- انتخاب مدل نامعتبر در هر دو provider بدون fallback خطا بدهد.
- یک درخواست عادی و یک درخواست streaming از اپ آزمایشی loopback کامل شود.

bootstrap و smoke test محلی را می‌توان با فرمان‌های زیر در Termux اجرا کرد:

```bash
bash ~/Rava/scripts/device-bootstrap.sh
bash ~/Rava/scripts/device-smoke.sh
```

برای ورود پایدار Gemini، یک profile جدا باز کنید. پس از ورود، capture مقدارها را
ذخیره می‌کند. مرورگر اختصاصی را هنگام استفاده از connector باز نگه دارید:

```bash
bash ~/Rava/scripts/start-gemini-login-browser.sh
.venv/bin/python ~/Rava/scripts/capture-gemini-session.py
```

برای کنترل نهایی هر دو provider در کنار هم اجرا کنید:

```bash
bash ~/Rava/scripts/run-device-combined-smoke.sh
```

پس از موفقیت تست، موتور اصلی را در پس‌زمینه اجرا کنید:

```bash
bash ~/Rava/scripts/start-rava.sh
curl http://127.0.0.1:8766/health
```

در اجراهای بعدی، وقتی ورود هر دو profile باقی مانده است، یک فرمان Chromiumها،
sidecar و موتور را بالا می‌آورد و wake lock را نیز فعال می‌کند:

```bash
bash ~/Rava/scripts/start-rava-stack.sh
```

برای راه‌اندازی مرورگر و connector مربوط به ChatGPT:

```bash
bash ~/Rava/scripts/start-termux-browser.sh https://chatgpt.com/
bash ~/Rava/scripts/install-chatgpt-sidecar.sh
bash ~/Rava/scripts/start-chatgpt-sidecar.sh
```

ورودها فقط در profileهای خصوصی Chromium همان گوشی نگهداری می‌شوند. روی گوشی دیگری
باید این مراحل و ورود حساب‌ها یک‌بار تکرار شوند.
