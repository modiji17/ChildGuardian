module.exports = (io) => {
    io.on('connection', (socket) => {
        console.log('New client connected:', socket.id);

        // When a device (target) registers, join a room named by its deviceId
        // When a device (target) registers, join a room named by its deviceId
        socket.on('register-device', (data) => {
            let rawData = typeof data === 'string' ? data : JSON.stringify(data);

            // This Regex extracts everything between "deviceId=" and the "}" or the end of the string
            const match = rawData.match(/deviceId=([^,}]+)/) || rawData.match(/"deviceId":"([^"]+)"/);

            const cleanId = match ? match[1] : rawData;

            console.log('--- REGISTRATION ATTEMPT ---');
            console.log('Raw data received:', rawData);
            console.log('Extracted Clean ID:', cleanId);

            if (cleanId) {
                socket.join(cleanId);
                console.log(`Success: Phone is now listening in room: ${cleanId}`);
            }
        });

        // When a viewer wants to start a session with a specific device
        socket.on('viewer-request', (data) => {
            const { deviceId } = data;
            console.log(`Viewer ${socket.id} requesting stream from ${deviceId}`);
            // Notify the target device (if online)
            io.to(deviceId).emit('viewer-wants-to-connect', { viewerId: socket.id });
        });

        // WebRTC offer from target to viewer
        socket.on('offer', (data) => {
            const { targetId, offer } = data; // targetId is actually the viewer's socket id? We need a consistent scheme.
            // Let's standardize: when target creates an offer, it sends to a specific viewerId
            io.to(data.viewerId).emit('offer', { offer: data.offer, targetId: socket.id });
        });

        // WebRTC answer from viewer to target
        socket.on('answer', (data) => {
            io.to(data.targetId).emit('answer', { answer: data.answer, viewerId: socket.id });
        });

        // ICE candidate from either side
        socket.on('ice-candidate', (data) => {
            // data.targetId is the intended recipient
            io.to(data.targetId).emit('ice-candidate', { candidate: data.candidate, senderId: socket.id });
        });

        socket.on('disconnect', () => {
            console.log('Client disconnected:', socket.id);
        });
    });
};