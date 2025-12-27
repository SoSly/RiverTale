const fs = require('fs');
const path = require('path');

const csvPath = path.join(__dirname, '..', 'run', 'client', 'heightmap_probe.csv');
const data = fs.readFileSync(csvPath, 'utf8').trim().split('\n').slice(1);

console.log(`Loaded ${data.length} samples\n`);

const samples = data.map(line => {
    const [x, z, continents, depth, erosion, ridges, height] = line.split(',');
    return {
        continents: parseFloat(continents),
        depth: parseFloat(depth),
        erosion: parseFloat(erosion),
        ridges: parseFloat(ridges),
        height: parseInt(height)
    };
});

function stats(arr) {
    const n = arr.length;
    let sum = 0, min = Infinity, max = -Infinity;
    for (const x of arr) {
        sum += x;
        if (x < min) min = x;
        if (x > max) max = x;
    }
    const mean = sum / n;
    let variance = 0;
    for (const x of arr) {
        variance += (x - mean) ** 2;
    }
    variance /= n;
    const std = Math.sqrt(variance);
    return { mean, std, min, max };
}

function correlation(x, y) {
    const n = x.length;
    const xMean = x.reduce((a, b) => a + b, 0) / n;
    const yMean = y.reduce((a, b) => a + b, 0) / n;

    let num = 0, denomX = 0, denomY = 0;
    for (let i = 0; i < n; i++) {
        const dx = x[i] - xMean;
        const dy = y[i] - yMean;
        num += dx * dy;
        denomX += dx * dx;
        denomY += dy * dy;
    }
    return num / Math.sqrt(denomX * denomY);
}

const heights = samples.map(s => s.height);
const continents = samples.map(s => s.continents);
const depths = samples.map(s => s.depth);
const erosions = samples.map(s => s.erosion);
const ridges = samples.map(s => s.ridges);

console.log('=== Variable Statistics ===');
console.log('Height:', stats(heights));
console.log('Continents:', stats(continents));
console.log('Depth:', stats(depths));
console.log('Erosion:', stats(erosions));
console.log('Ridges:', stats(ridges));

console.log('\n=== Correlations with Height ===');
console.log(`Continents: ${correlation(continents, heights).toFixed(4)}`);
console.log(`Depth: ${correlation(depths, heights).toFixed(4)}`);
console.log(`Erosion: ${correlation(erosions, heights).toFixed(4)}`);
console.log(`Ridges: ${correlation(ridges, heights).toFixed(4)}`);

function multipleRegression(X, y) {
    const n = y.length;
    const k = X[0].length;

    const XtX = Array(k).fill(null).map(() => Array(k).fill(0));
    const Xty = Array(k).fill(0);

    for (let i = 0; i < n; i++) {
        for (let j = 0; j < k; j++) {
            Xty[j] += X[i][j] * y[i];
            for (let l = 0; l < k; l++) {
                XtX[j][l] += X[i][j] * X[i][l];
            }
        }
    }

    for (let i = 0; i < k; i++) {
        const pivot = XtX[i][i];
        for (let j = 0; j < k; j++) {
            XtX[i][j] /= pivot;
        }
        Xty[i] /= pivot;

        for (let j = 0; j < k; j++) {
            if (i !== j) {
                const factor = XtX[j][i];
                for (let l = 0; l < k; l++) {
                    XtX[j][l] -= factor * XtX[i][l];
                }
                Xty[j] -= factor * Xty[i];
            }
        }
    }

    return Xty;
}

function rSquared(predicted, actual) {
    const n = actual.length;
    const mean = actual.reduce((a, b) => a + b, 0) / n;

    let ssRes = 0, ssTot = 0;
    for (let i = 0; i < n; i++) {
        ssRes += (actual[i] - predicted[i]) ** 2;
        ssTot += (actual[i] - mean) ** 2;
    }
    return 1 - ssRes / ssTot;
}

const X = samples.map(s => [1, s.continents, s.depth, s.erosion, s.ridges]);
const coeffs = multipleRegression(X, heights);

console.log('\n=== Multiple Linear Regression ===');
console.log(`height = ${coeffs[0].toFixed(2)} + ${coeffs[1].toFixed(2)}*continents + ${coeffs[2].toFixed(2)}*depth + ${coeffs[3].toFixed(2)}*erosion + ${coeffs[4].toFixed(2)}*ridges`);

const predicted = samples.map(s =>
    coeffs[0] + coeffs[1]*s.continents + coeffs[2]*s.depth + coeffs[3]*s.erosion + coeffs[4]*s.ridges
);
const r2 = rSquared(predicted, heights);
console.log(`R² = ${r2.toFixed(4)}`);

const errors = samples.map((s, i) => Math.abs(predicted[i] - s.height));
let meanError = 0, maxError = 0;
for (const err of errors) {
    meanError += err;
    if (err > maxError) maxError = err;
}
meanError /= errors.length;
console.log(`Mean Absolute Error: ${meanError.toFixed(2)} blocks`);
console.log(`Max Error: ${maxError.toFixed(2)} blocks`);

console.log('\n=== Error Distribution ===');
const buckets = [0, 0, 0, 0, 0, 0];
for (const err of errors) {
    if (err < 1) buckets[0]++;
    else if (err < 2) buckets[1]++;
    else if (err < 5) buckets[2]++;
    else if (err < 10) buckets[3]++;
    else if (err < 20) buckets[4]++;
    else buckets[5]++;
}
console.log(`< 1 block: ${(100*buckets[0]/errors.length).toFixed(1)}%`);
console.log(`1-2 blocks: ${(100*buckets[1]/errors.length).toFixed(1)}%`);
console.log(`2-5 blocks: ${(100*buckets[2]/errors.length).toFixed(1)}%`);
console.log(`5-10 blocks: ${(100*buckets[3]/errors.length).toFixed(1)}%`);
console.log(`10-20 blocks: ${(100*buckets[4]/errors.length).toFixed(1)}%`);
console.log(`> 20 blocks: ${(100*buckets[5]/errors.length).toFixed(1)}%`);

console.log('\n=== Sample Predictions (first 10) ===');
for (let i = 0; i < 10; i++) {
    const s = samples[i];
    const pred = coeffs[0] + coeffs[1]*s.continents + coeffs[2]*s.depth + coeffs[3]*s.erosion + coeffs[4]*s.ridges;
    console.log(`Actual: ${s.height}, Predicted: ${pred.toFixed(1)}, Error: ${(pred - s.height).toFixed(1)}`);
}
