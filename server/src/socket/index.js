module.exports = (io) => {
  io.on('connection', (socket) => {
    console.log('New client connected:', socket.id);

    socket.on('register-device', (data) => {
      console.log('Device registered:', data);
      socket.join(data.deviceId);
    });

    socket.on('disconnect', () => {
      console.log('Client disconnected:', socket.id);
    });
  });
};