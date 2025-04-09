// components/uploading.jsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

const Uploading = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileName, setFileName] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleLogout = () => {
    // Add any logout logic here (clearing tokens, etc.)
    navigate('/'); // Redirect to landing page
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    setError(''); // Reset error
    
    if (!file) return;

    // Validate file type
    const validAudioTypes = [
      'audio/mpeg', // MP3
      'audio/wav',  // WAV
      'audio/ogg',  // OGG
      'audio/aac',  // AAC
      'audio/x-m4a', // M4A
      'audio/webm'  // WEBM
    ];

    const fileExt = file.name.split('.').pop().toLowerCase();
    const validExtensions = ['mp3', 'wav', 'ogg', 'aac', 'm4a', 'webm'];

    if (
      !validAudioTypes.includes(file.type) || 
      !validExtensions.includes(fileExt)
    ) {
      setError('Please upload only audio files (MP3, WAV, OGG, AAC, M4A, WEBM)');
      return;
    }

    setSelectedFile(file);
    setFileName(file.name);
  };

  const handleClick = () => {
    document.getElementById('fileInput').click();
  };

  const removeFile = () => {
    setSelectedFile(null);
    setFileName('');
    document.getElementById('fileInput').value = '';
  };

  const handleFileUpload = (e) => {
    e.preventDefault();
    if (!selectedFile) {
      setError('Please select an audio file first');
      return;
    }

    console.log("Uploading file:", selectedFile.name);
    // Your upload logic here
  };

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header with Logout Button */}
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <Link to="/" className="text-2xl font-bold text-white">AudioScholar</Link>
          <button 
            onClick={handleLogout}
            className="text-gray-300 hover:text-indigo-400 transition-colors py-2 px-4 rounded hover:bg-white hover:bg-opacity-10"
          >
            Logout
          </button>
        </div>
      </header>

      {/* Main Upload Content */}
      <main className="flex-grow flex items-center justify-center py-12 bg-gray-50">
        <div className="container mx-auto px-4 max-w-4xl">
          <div className="bg-white rounded-lg shadow-xl p-8 md:p-10">
            <h1 className="text-3xl font-bold text-gray-800 mb-6">Upload Audio</h1>
            
            {/* Hidden file input */}
            <input 
              type="file" 
              id="fileInput" 
              onChange={handleFileChange}
              className="hidden" 
              accept="audio/*,.mp3,.wav,.ogg,.aac,.m4a,.webm" 
            />
            
            {/* File Upload Section */}
            <div className="mb-4">
              <div 
                onClick={handleClick}
                className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer hover:bg-gray-50 transition"
              >
                {fileName ? (
                  <div className="flex items-center justify-center gap-4">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                    </svg>
                    <p className="text-gray-800 font-medium truncate max-w-xs">{fileName}</p>
                    <button 
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        removeFile();
                      }}
                      className="text-red-500 hover:text-red-700"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </div>
                ) : (
                  <div>
                    <svg xmlns="http://www.w3.org/2000/svg" className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                    </svg>
                    <p className="text-gray-600 mb-2">Click to select audio file or drag and drop</p>
                    <p className="text-xs text-gray-500">Supports: MP3, WAV, OGG, AAC, M4A, WEBM</p>
                  </div>
                )}
              </div>
              {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
            </div>

            {/* Audio Details Form */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                <span className="font-bold">Title:</span>
                <span className="ml-1 text-gray-500">Enter title for your audio</span>
              </label>
              <input
                type="text"
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A]"
                placeholder="Enter title for your upload"
              />
            </div>

            <div className="mb-8">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                <span className="font-bold">Description (Optional):</span>
              </label>
              <textarea
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A] min-h-[100px]"
                placeholder="Add a description for your upload"
              />
            </div>

            <button 
              onClick={handleFileUpload}
              disabled={!selectedFile}
              className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition ${
                !selectedFile ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              Upload Audio
            </button>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Uploading;