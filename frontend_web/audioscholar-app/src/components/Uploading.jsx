// components/Uploading.jsx
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
// Import the base API URL from your auth service file
import { API_BASE_URL } from '../services/authService'; // <--- Assuming you export API_BASE_URL from here

// --- Define ALLOWED types/extensions OUTSIDE the component ---
// Moved these definitions outside the component function scope
const VALID_AUDIO_TYPES = [
  'audio/mpeg', 'audio/mp3', // MP3
  'audio/wav', 'audio/x-wav', // WAV (added x-wav for broader compatibility)
  'audio/ogg', // OGG
  'audio/aac', // AAC
  'audio/x-m4a', // M4A
  'audio/webm', // WEBM
  'audio/flac', // FLAC (Added based on backend controller)
  'audio/aiff', 'audio/x-aiff' // AIFF (Added based on backend controller)
];

const VALID_EXTENSIONS = ['mp3', 'wav', 'ogg', 'aac', 'm4a', 'webm', 'flac', 'aiff']; // Moved outside
// --- End of constants ---


const Uploading = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileName, setFileName] = useState('');
  // Add state for title and description
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('AuthToken');
    localStorage.removeItem('userId');
    navigate('/');
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    setError('');

    if (!file) return;

    // Validate file type and extension
    // *** Now using the constants defined OUTSIDE the component ***
    const fileExt = file.name.split('.').pop().toLowerCase();

    if (
      !VALID_AUDIO_TYPES.includes(file.type.toLowerCase()) || // Using VALID_AUDIO_TYPES
      !VALID_EXTENSIONS.includes(fileExt) // Using VALID_EXTENSIONS
    ) {
      setError(`Please upload only audio files. Allowed types: ${VALID_EXTENSIONS.join(', ').toUpperCase()}`);
      setSelectedFile(null);
      setFileName('');
      document.getElementById('fileInput').value = '';
      return;
    }

    setSelectedFile(file);
    setFileName(file.name);
    setTitle(file.name.replace(/\.[^/.]+$/, ""));
  };

  const handleClick = () => {
    if (!loading) {
       document.getElementById('fileInput').click();
    }
  };

  const removeFile = () => {
    setSelectedFile(null);
    setFileName('');
    setTitle('');
    setDescription('');
    document.getElementById('fileInput').value = '';
    setError('');
  };

  const handleFileUpload = async (e) => {
     e.preventDefault();
     setError('');

     if (!selectedFile) {
       setError('Please select an audio file first');
       return;
     }

     setLoading(true);

     // Retrieve the token using the correct key
     const AuthToken = localStorage.getItem('AuthToken');

     if (!AuthToken) {
       setError('Authentication token not found. Please sign in again.');
       setLoading(false);
       navigate('/signin');
       return;
     }

     const formData = new FormData();
     formData.append('file', selectedFile);
     if (title) {
         formData.append('title', title.trim());
     }
     if (description) {
         formData.append('description', description.trim());
     }

     console.log("Preparing to upload file:", selectedFile.name);

     try {
       const UPLOAD_URL = `${API_BASE_URL}api/audio/upload`;

       console.log(`Attempting to upload to: ${UPLOAD_URL}`);

       const response = await fetch(UPLOAD_URL, {
         method: 'POST',
         headers: {
           // Use the correct token variable
           'Authorization': `Bearer ${AuthToken}`,
         },
         body: formData,
       });

       if (!response.ok) {
         let errorMsg = `Upload failed with status: ${response.status}`;
         try {
           const errorBody = await response.json();
           errorMsg = errorBody.message || errorMsg;
         } catch (jsonError) {
            errorMsg = `Upload failed: ${response.status} ${response.statusText}`;
         }
         console.error("Upload failed:", errorMsg);
         setError(errorMsg);
         if (response.status === 401) {
              console.log("Token expired or invalid, redirecting to sign in.");
             // Remove the correct token key
             localStorage.removeItem('AuthToken');
             localStorage.removeItem('userId');
              navigate('/signin');
         }

       } else {
         const result = await response.json();
         console.log("Upload successful:", result);
         setError('Upload successful!');
         removeFile(); // Clears selectedFile, fileName, title, description
       }

     } catch (err) {
       console.error("Error during file upload:", err);
       setError(`An error occurred during upload: ${err.message}`);
     } finally {
       setLoading(false);
     }
  };


  return (
    <div className="min-h-screen flex flex-col">
      {/* Header ... */}
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <Link to="/" className="text-2xl font-bold text-white">AudioScholar</Link>
          <div className="flex items-center space-x-2">
            <Link
              to="/dashboard"
              className="text-gray-300 hover:text-indigo-400 transition-colors py-2 px-4 rounded hover:bg-white hover:bg-opacity-10"
            >
              Back to Dashboard
            </Link>
            <Link
              to="/profile"
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <span className="hidden sm:inline">Profile</span>
              <span className="sm:hidden"></span>
            </Link>
            <button
              onClick={handleLogout}
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
              <span className="hidden sm:inline">Logout</span>
              <span className="sm:hidden"></span>
            </button>
          </div>
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
              // *** Now using the constants defined OUTSIDE the component ***
              accept={VALID_AUDIO_TYPES.join(',')} // <--- Corrected line to use the constant
            />

            {/* File Upload Section */}
            <div className="mb-4">
              <div
                onClick={handleClick}
                className={`border-2 border-dashed border-gray-300 rounded-lg p-8 text-center cursor-pointer transition ${loading ? 'opacity-60 cursor-not-allowed' : 'hover:bg-gray-50'}`}
              >
                {fileName ? (
                  <div className="flex items-center justify-center gap-4">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                    </svg>
                    <p className="text-gray-800 font-medium truncate max-w-xs">{fileName}</p>
                    {!loading && (
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
                    )}
                  </div>
                ) : (
                  <div>
                    <svg xmlns="http://www.w3.org/2000/svg" className="mx-auto h-12 w-12 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                    </svg>
                    <p className="text-gray-600 mb-2">Click to select audio file or drag and drop</p>
                    <p className="text-xs text-gray-500">Supports: {VALID_EXTENSIONS.join(', ').toUpperCase()}</p> {/* Using VALID_EXTENSIONS */}
                  </div>
                )}
              </div>
              {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
            </div>

            {/* Audio Details Form */}
            {/* ... (Title and Description inputs - they use state directly, which is correct) ... */}
             <div className="mb-6">
               <label htmlFor="audio-title" className="block text-sm font-medium text-gray-700 mb-2">
                 <span className="font-bold">Title:</span>
                 <span className="ml-1 text-gray-500">Enter title for your audio</span>
               </label>
               <input
                 type="text"
                 id="audio-title"
                 className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A]"
                 placeholder="Enter title for your upload"
                 value={title}
                 onChange={(e) => setTitle(e.target.value)}
                 disabled={loading}
               />
             </div>

             <div className="mb-8">
               <label htmlFor="audio-description" className="block text-sm font-medium text-gray-700 mb-2">
                 <span className="font-bold">Description (Optional):</span>
               </label>
               <textarea
                  id="audio-description"
                 className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-[#2D8A8A] focus:border-[#2D8A8A] min-h-[100px]"
                 placeholder="Add a description for your upload"
                 value={description}
                 onChange={(e) => setDescription(e.target.value)}
                 disabled={loading}
               />
             </div>


            <button
              onClick={handleFileUpload}
              disabled={!selectedFile || loading}
              className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition ${
                 (!selectedFile || loading) ? 'opacity-50 cursor-not-allowed' : ''
              }`}
            >
              {loading ? 'Uploading...' : 'Upload Audio'}
            </button>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Uploading;