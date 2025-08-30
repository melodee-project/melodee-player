#!/bin/bash

# Performance Benchmarking Script
# Usage: ./run_benchmarks.sh [baseline|improved]

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_TYPE=${1:-"test"}
REPORT_DIR="performance_reports/${REPORT_TYPE}_${TIMESTAMP}"

echo "🚀 Running Performance Benchmarks"
echo "Report type: $REPORT_TYPE"
echo "Timestamp: $TIMESTAMP"
echo "Report directory: $REPORT_DIR"
echo ""

# Note: Assuming device/emulator is running in Android Studio
echo "📱 Make sure your Android device/emulator is running in Android Studio"

echo "📱 Device connected, starting benchmarks..."
echo ""

# Run benchmarks
echo "⏱️  Running startup benchmarks..."
./gradlew benchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.melodee.autoplayer.benchmark.StartupBenchmark

echo ""
echo "📊 Running scroll performance benchmarks..."
./gradlew benchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.melodee.autoplayer.benchmark.ScrollPerformanceBenchmark

echo ""
echo "🧭 Running navigation benchmarks..."
./gradlew benchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.melodee.autoplayer.benchmark.NavigationBenchmark

# Create report directory
mkdir -p "$REPORT_DIR"

# Copy benchmark results
if [ -d "src/benchmark/build/reports/androidTests/connected" ]; then
    cp -r src/benchmark/build/reports/androidTests/connected/* "$REPORT_DIR/"
    echo ""
    echo "✅ Benchmarks complete!"
    echo "📈 Reports saved to: $REPORT_DIR"
    echo ""
    echo "View results:"
    echo "open $REPORT_DIR/index.html"
    
    # Create summary
    echo "## $REPORT_TYPE Performance Report - $TIMESTAMP" > "$REPORT_DIR/summary.md"
    echo "" >> "$REPORT_DIR/summary.md"
    echo "### Benchmark Results" >> "$REPORT_DIR/summary.md"
    echo "- **Startup Performance**: See StartupBenchmark results" >> "$REPORT_DIR/summary.md"
    echo "- **Scroll Performance**: See ScrollPerformanceBenchmark results" >> "$REPORT_DIR/summary.md"
    echo "- **Navigation Performance**: See NavigationBenchmark results" >> "$REPORT_DIR/summary.md"
    echo "" >> "$REPORT_DIR/summary.md"
    echo "Generated: $(date)" >> "$REPORT_DIR/summary.md"
    
    echo "📝 Summary created: $REPORT_DIR/summary.md"
else
    echo "❌ No benchmark reports found. Check if tests ran successfully."
    exit 1
fi

echo ""
echo "🔄 To compare with previous results:"
echo "1. For baseline: ./run_benchmarks.sh baseline"
echo "2. Make performance improvements"
echo "3. For comparison: ./run_benchmarks.sh improved"
echo "4. Compare reports in performance_reports/ directory"