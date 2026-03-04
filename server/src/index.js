const app = require('./app');
const http = require('http');
const { Server } = require('socket.io');
const config = require('./config/config');

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*", // In production, restrict this
    methods: ["GET", "POST"]
  }
});

app.set('io', io);

// Socket.io event handlers
require('./socket')(io);

const PORT = process.env.PORT || 3000;

server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});