import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';
import { FiCheckCircle, FiFile, FiLoader, FiUpload, FiXCircle } from 'react-icons/fi';

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
  const [previewUrl, setPreviewUrl] = useState(null);
  const [success, setSuccess] = useState(null);
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  useEffect(() => {
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = 'Upload in progress. Are you sure you want to leave?';
      return 'Upload in progress. Are you sure you want to leave?';
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
    setSuccess(null);

    if (!file) return;

    const fileExt = file.name.split('.').pop().toLowerCase();

    console.log(`Selected File: ${file.name}, Type: ${file.type}, Ext: ${fileExt}`);

    if (
      !VALID_AUDIO_TYPES.includes(file.type.toLowerCase()) && 
      !VALID_EXTENSIONS.includes(fileExt)
    ) {
      setError(`Please upload only audio files. Allowed types: ${VALID_EXTENSIONS.join(', ').toUpperCase()}`);
      setSelectedFile(null);
      setFileName('');
      setPreviewUrl(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      return;
    }

    setSelectedFile(file);
    setFileName(file.name);
    setTitle(file.name.replace(/\.[^/.]+$/, ""));

    // Create a preview URL
    const reader = new FileReader();
    reader.onloadend = () => {
      setPreviewUrl(reader.result);
    };
    reader.onerror = () => {
      console.error("Error reading file for preview");
      setError("Could not generate file preview.");
      setPreviewUrl(null); // Clear preview on error
    };
    reader.readAsDataURL(file); // Read file as Data URL
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
    setPreviewUrl(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    setError('');
    setSuccess(null);
  };

  const handleFileUpload = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess(null);

    if (!selectedFile) {
      setError('Please select an audio file first');
      return;
    }
     if (!title.trim()) {
       setError('Please enter a title for the recording.');
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
    formData.append('title', title.trim());
    if (description.trim()) {
      formData.append('description', description.trim());
    }

    console.log("Preparing to upload file:", selectedFile.name, "with title:", title.trim());

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
        setSuccess(null);
        if (response.status === 401) {
          console.log("Token expired or invalid, redirecting to sign in.");
          localStorage.removeItem('AuthToken');
          localStorage.removeItem('userId');
          navigate('/signin');
        }

      } else {
        const result = await response.json();
        console.log("Upload successful:", result);
        setSuccess('File uploaded successfully! Processing has started.');
        setSelectedFile(null);
        setFileName('');
        setDescription('');
        setPreviewUrl(null);
        if (fileInputRef.current) {
          fileInputRef.current.value = '';
        }
      }

    } catch (err) {
      console.error("Error during file upload:", err);
      setError(`An error occurred during upload: ${err.message}`);
      setSuccess(null);
    } finally {
      setLoading(false);
    }
  };


  return (
    <div className="relative min-h-screen flex flex-col">
      {loading && (
        <div className="fixed inset-0 bg-black bg-opacity-50 z-50 flex flex-col items-center justify-center">
          <FiLoader className="animate-spin h-10 w-10 text-white mb-3" />
          <p className="text-white text-lg font-medium">Uploading, please wait...</p>
          <p className="text-gray-300 text-sm mt-1">Navigating away may interrupt the upload.</p>
        </div>
      )}

      <title>AudioScholar - Upload Recording</title>
      <Header />

      <main className="flex-grow flex items-center justify-center py-12 bg-gray-50">
        <div className="container mx-auto px-4 max-w-4xl">
          <div className="bg-white rounded-lg shadow-xl p-8 md:p-10">
            <h1 className="text-3xl font-bold text-gray-800 mb-6 text-center">Upload Audio</h1>

            <input
              type="file"
              id="fileInput"
              ref={fileInputRef}
              onChange={handleFileChange}
              className="hidden"
              accept={VALID_AUDIO_TYPES.join(',')}
              disabled={loading}
            />

            <div className="mb-6">
              <div
                onClick={handleClick}
                className={`border-2 border-dashed border-gray-300 rounded-lg p-10 text-center transition-colors duration-200 ${loading ? 'opacity-60 cursor-not-allowed bg-gray-100' : 'cursor-pointer hover:bg-gray-100 hover:border-teal-400'}`}
              >
                {fileName ? (
                  <div className="flex flex-col items-center justify-center gap-3">
                    <FiFile className="h-10 w-10 text-teal-600" />
                    <p className="text-gray-800 font-medium text-lg truncate max-w-xs">{fileName}</p>
                    {!loading && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          removeFile();
                        }}
                        className="mt-2 text-red-600 hover:text-red-800 transition-colors duration-150 p-1 rounded-full hover:bg-red-100 inline-flex items-center gap-1 text-xs font-medium"
                        title="Remove file"
                      >
                        <FiXCircle className="h-4 w-4"/> Remove
                      </button>
                    )}
                  </div>
                ) : (
                  <div>
                    <FiUpload className="mx-auto h-12 w-12 text-gray-400" />
                    <p className="text-gray-600 mt-2 mb-1">Click to select audio file or drag and drop</p>
                    <p className="text-xs text-gray-500">Supports: {VALID_EXTENSIONS.join(', ').toUpperCase()}</p>
                  </div>
                )}
              </div>
              {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
            </div>

            {previewUrl && !loading && (
              <div className="mt-4 pt-4 border-t border-gray-200">
                <h3 className="text-sm font-medium text-gray-700 mb-2">Preview:</h3>
                <audio controls src={previewUrl} className="w-full h-10" >
                  Your browser does not support the audio element.
                </audio>
              </div>
            )}

            <form onSubmit={handleFileUpload} className="space-y-6">
              <div>
                <label htmlFor="audio-title" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Title:</span>
                  <span className="ml-1 text-gray-500">Enter title for your audio</span>
                </label>
                <input
                  type="text"
                  id="audio-title"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition duration-150 ease-in-out shadow-sm disabled:bg-gray-100 disabled:cursor-not-allowed"
                  placeholder="Enter title for your upload (required)"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  disabled={loading}
                  required
                />
              </div>

              <div>
                <label htmlFor="audio-description" className="block text-sm font-medium text-gray-700 mb-1">
                  <span className="font-bold">Description (Optional):</span>
                </label>
                <textarea
                  id="audio-description"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-teal-500 focus:border-teal-500 min-h-[100px] transition duration-150 ease-in-out shadow-sm disabled:bg-gray-100 disabled:cursor-not-allowed"
                  placeholder="Add a description for your upload"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={loading}
                />
              </div>

              <div className="h-6">
                {success && <p className="text-sm text-green-600 text-center flex items-center justify-center gap-1"><FiCheckCircle/>{success}</p>}
              </div>

              <button
                type="submit"
                disabled={!selectedFile || loading || !title.trim()}
                className={`w-full bg-[#2D8A8A] text-white py-3 px-4 rounded-lg font-medium flex items-center justify-center gap-2 transition-all duration-200 ease-in-out ${(!selectedFile || loading || !title.trim()) ? 'opacity-50 cursor-not-allowed' : 'hover:bg-[#236b6b] hover:shadow-md transform hover:-translate-y-0.5'
                  }`}
              >
                {loading ? <><FiLoader className="animate-spin h-5 w-5" /> Uploading...</> : <><FiUpload className="h-5 w-5"/> Upload Audio</>}
              </button>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Uploading;