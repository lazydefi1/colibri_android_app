# BLE Large Response Handling Fixes

## Problem
The ESP32 was sending large JSON responses (e.g., listMethods with many methods), but the Android app was receiving "RPC call failed" instead of the actual response.

## Root Causes
1. **BLE MTU Limitation**: BLE characteristics can only send ~20-244 bytes per notification
2. **Single Notification Assumption**: The original code expected responses in a single notification
3. **Missing Descriptor Setup**: Notifications weren't properly enabled on the client characteristic configuration descriptor
4. **No Response Assembly**: Large responses split across multiple notifications weren't being reassembled

## Solutions Implemented

### 1. Chunked Response Handling
- Added `responseBuffer` to accumulate multiple notification chunks
- Implemented `isCompleteJsonResponse()` to detect when full JSON is received
- Enhanced logging to show chunk sizes and partial response progress

### 2. Proper Notification Setup
- Added Client Characteristic Configuration Descriptor (CCCD) setup
- Used standard UUID `00002902-0000-1000-8000-00805f9b34fb` for notification enable
- Added proper descriptor write confirmation

### 3. Persistent GATT Callback
- Created a persistent connection callback that delegates to RPC-specific callbacks
- Properly handles connection, notification, and write events
- Maintains callback references across multiple RPC calls

### 4. Enhanced Timeouts and Error Handling
- Increased RPC timeout from 10s to 30s for large responses
- Added detailed chunk-by-chunk logging
- Better error messages for debugging

### 5. JSON Validation
- Implemented bracket/brace counting for JSON completeness detection
- Handles escaped characters and strings properly
- Validates response ends with proper JSON terminator

## Configuration
- Max response size: 8KB (`BleConstants.ConnectionSettings.MAX_RESPONSE_SIZE`)
- RPC timeout: 30 seconds (`BleConstants.ConnectionSettings.RPC_TIMEOUT_MS`)
- MTU: 247 bytes (`BleConstants.ConnectionSettings.DEFAULT_MTU`)

## Debug Messages
The app now provides detailed logs:
- "Received chunk (X bytes): [preview]..."
- "Partial response so far: X bytes"
- "Complete response received (X bytes)"
- Notification descriptor enable/disable status

## Testing
These changes should resolve the issue where large ESP32 responses (like comprehensive listMethods results) were failing to be received properly by the Android app.