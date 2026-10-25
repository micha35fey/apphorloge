# Synthetisch applaus: veel losse klappers, elk met eigen tempo en klankkleur, plus wat galm.
import numpy as np, wave
rng = np.random.default_rng(7)
SR = 22050
DUR = 10.0
n = int(SR * DUR)
out = np.zeros(n)

def bandpass(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    # zachte randen
    mask = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * mask, len(x))

kernel_len = int(0.03 * SR)
t = np.arange(kernel_len) / SR
groups = [(500, 1800), (800, 2600), (1200, 3800), (700, 2200), (1500, 5000)]
for lo, hi in groups:
    train = np.zeros(n)
    for _ in range(18):  # klappers per groep
        period = rng.uniform(0.17, 0.32)
        start = rng.uniform(0.0, 0.6)
        stop = rng.uniform(7.8, DUR - 0.3)
        loud = rng.uniform(0.4, 1.0)
        tt = start
        while tt < stop:
            i = int(tt * SR)
            # klappers worden aan het eind zachter
            fade = min(1.0, (stop - tt) / 1.5)
            train[i] += loud * fade * rng.uniform(0.6, 1.0)
            tt += period * rng.uniform(0.85, 1.15)
    burst = rng.standard_normal(kernel_len) * np.exp(-t / rng.uniform(0.003, 0.006))
    burst[: int(0.0015 * SR)] *= np.linspace(0, 1, int(0.0015 * SR))
    group = np.fft.irfft(np.fft.rfft(train, 2 * n) * np.fft.rfft(burst, 2 * n), 2 * n)[:n]
    out += bandpass(group, lo, hi)

# galm van een kleine hal
rev_len = int(0.5 * SR)
rev = rng.standard_normal(rev_len) * np.exp(-np.arange(rev_len) / (0.12 * SR))
wet = np.fft.irfft(np.fft.rfft(out, 2 * n) * np.fft.rfft(rev, 2 * n), 2 * n)[:n]
out = out + 0.25 * wet / np.max(np.abs(wet)) * np.max(np.abs(out))

# zacht in- en uitfaden
env = np.ones(n)
fi = int(0.3 * SR); fo = int(1.0 * SR)
env[:fi] = np.linspace(0, 1, fi)
env[-fo:] = np.linspace(1, 0, fo)
out *= env
out = out / np.max(np.abs(out)) * 0.9
pcm = (out * 32767).astype(np.int16)
with wave.open("applause.wav", "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
# korte samenvatting: luidheid per seconde
rms = [float(np.sqrt(np.mean(out[i*SR:(i+1)*SR]**2))) for i in range(int(DUR))]
print("rms per seconde:", " ".join(f"{r:.2f}" for r in rms))
