import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';

const VALID_AUDIO_TYPES = [
  'audio/mpeg', 'audio/mp3',
  'audio/wav', 'audio/x-wav',
  'audio/aiff', 'audio/x-aiff',
  'audio/aac', 'audio/vnd.dlna.adts',
  'audio/ogg', 'application/ogg',
  'audio/flac', 'audio/x-flac',
];

const VALID_EXTENSIONS = ['mp3', 'wav', 'aiff', 'aac', 'ogg', 'flac'];


const Uploading = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileName, setFileName] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  useEffect(() => {
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
      return 'Are you sure you want to leave? Your upload is still in progress.';
    };

    if (loading) {
      window.addEventListener('beforeunload', handleBeforeUnload);
    } else {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    }

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [loading]);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    setError('');

    if (!file) return;

    const fileExt = file.name.split('.').pop().toLowerCase();

    console.log(`Selected File: ${file.name}, Type: ${file.type}, Ext: ${fileExt}`);

    if (
      !VALID_AUDIO_TYPES.includes(file.type.toLowerCase()) ||
      !VALID_EXTENSIONS.includes(fileExt)
    ) {
      setError(`Please upload only audio files. Allowed types: ${VALID_EXTENSIONS.join(', ').toUpperCase()}`);
      setSelectedFile(null);
      setFileName('');
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    setSelectedFile(file);
    setFileName(file.name);
    setTitle(file.name.replace(/\.[^/.]+$/, ""));
  };

  const handleClick = () => {
    if (!loading && fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const removeFile = () => {
    setSelectedFile(null);
    setFileName('');
    setTitle('');
    setDescription('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
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
          console.error("Error parsing error response body:", jsonError);
          errorMsg = `Upload failed: ${response.status} ${response.statusText}`;
        }
        console.error("Upload failed:", errorMsg);
        setError(errorMsg);
        if (response.status === 401) {
          console.log("Token expired or invalid, redirecting to sign in.");
          localStorage.removeItem('AuthToken');
          localStorage.removeItem('userId');
          navigate('/signin');
        }

      } else {
        const result = await response.json();
        console.log("Upload successful:", result);
        setError('Upload successful!');
        removeFile();
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
      <title>AudioScholar - Upload Recording</title>
      <Header />

      <main className="flex-grow flex items-center justify-center py-12 bg-gray-50">
        <div className="container mx-auto px-4 max-w-4xl">
          <div className="bg-white rounded-lg shadow-xl p-8 md:p-10">
            <h1 className="text-3xl font-bold text-gray-800 mb-6">Upload Audio</h1>

            <input
              type="file"
              id="fileInput"
              ref={fileInputRef}
              onChange={handleFileChange}
              className="hidden"
              accept={VALID_AUDIO_TYPES.join(',')}
            />

            <div className="mb-6">
              <div
                onClick={handleClick}
                className={`border-2 border-dashed border-gray-300 rounded-lg p-10 text-center cursor-pointer transition-colors duration-200 ${loading ? 'opacity-60 cursor-not-allowed bg-gray-100' : 'hover:bg-gray-100 hover:border-teal-400'}`}
              >
                {fileName ? (
                  <div className="flex flex-col items-center justify-center gap-3">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-10 w-10 text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                    </svg>
                    <p className="text-gray-800 font-medium text-lg truncate max-w-xs">{fileName}</p>
                    {!loading && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          removeFile();
                        }}
                        className="text-red-600 hover:text-red-800 transition-colors duration-150 p-1 rounded-full hover:bg-red-100"
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
                    <p className="text-xs text-gray-500">Supports: {VALID_EXTENSIONS.join(', ').toUpperCase()}</p>
                  </div>
                )}
              </div>
              {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
            </div>

            <form onSubmit={handleFileUpload} className="space-y-6">
              <div className="mb-0">
                <label htmlFor="audio-title" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Title:</span>
                  <span className="ml-1 text-gray-500">Enter title for your audio</span>
                </label>
                <input
                  type="text"
                  id="audio-title"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm"
                  placeholder="Enter title for your upload"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div className="mb-0">
                <label htmlFor="audio-description" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Description (Optional):</span>
                </label>
                <textarea
                  id="audio-description"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 min-h-[100px] transition duration-150 ease-in-out shadow-sm"
                  placeholder="Add a description for your upload"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={loading}
                />
              </div>


              <button
                type="submit"
                disabled={!selectedFile || loading}
                className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition-all duration-200 ease-in-out ${(!selectedFile || loading) ? 'opacity-50 cursor-not-allowed' : 'hover:shadow-md transform hover:-translate-y-0.5'
                  }`}
              >
                {loading ? 'Uploading...' : 'Upload Audio'}
              </button>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Uploading;