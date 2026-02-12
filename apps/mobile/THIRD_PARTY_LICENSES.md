# Third-Party Licenses

## Tandem BLE Protocol Implementation

The Bluetooth Low Energy protocol implementation in this application is informed
by research and reverse-engineering work from the following MIT-licensed projects:

### pumpX2

- Repository: https://github.com/jwoglom/pumpX2
- License: MIT
- Copyright: James Woglom

Java library implementing a reverse-engineered Bluetooth protocol for Tandem
insulin pumps. Our Kotlin implementation is based on studying this protocol
documentation and message format. No code is imported or used as a runtime
dependency.

### controlX2

- Repository: https://github.com/jwoglom/controlX2
- License: MIT
- Copyright: James Woglom

Android + Wear OS reference application for Tandem insulin pumps. Architecture
patterns for BLE service management and pump pairing were studied from this
project. No code is imported or used as a runtime dependency.

---

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
