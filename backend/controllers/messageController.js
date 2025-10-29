const Message = require('../models/Message');
const Room = require('../models/Room');
const File = require('../models/File');

const messageController = {
  // 채팅방의 메시지 목록 조회
  async loadMessages(req, res) {
      res.status(500).json({
          success: false,
          message: '미구현.'
      });
  }
};

module.exports = messageController;
