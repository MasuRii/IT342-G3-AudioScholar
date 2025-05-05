import axios from 'axios';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FiAlertTriangle, FiCheckCircle, FiClock, FiExternalLink, FiFile, FiLoader, FiTrash2, FiUploadCloud } from 'react-icons/fi';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';

// Define terminal statuses
const TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'PROCESSING_HALTED_UNSUITABLE_CONTENT', 'PROCESSING_HALTED_NO_SPEECH'];
// Define uploading statuses
const UPLOADING_STATUSES = ['UPLOADING_TO_STORAGE', 'UPLOAD_IN_PROGRESS'];
// Define timeout for display purposes (in seconds)
const UPLOAD_TIMEOUT_SECONDS = 10 * 60; // 10 minutes

const RecordingList = () => {
    const [recordings, setRecordings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const navigate = useNavigate();
    const pollIntervalRef = useRef(null); // Ref to store interval ID
    const isMountedRef = useRef(true); // Ref to track component mount status

    const fetchRecordings = useCallback(async () => {
        console.log("Fetching recordings...");
        const token = localStorage.getItem('AuthToken');
        if (!token) {
            setError("User not authenticated. Please log in.");
            setLoading(false);
            navigate('/signin');
            return null; // Return null to indicate fetch didn't complete
        }

        try {
            const url = `${API_BASE_URL}api/audio/metadata`;
            const response = await axios.get(url, {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            
            // Sort recordings by upload timestamp descending
            const sortedRecordings = response.data.sort((a, b) => 
                (b.uploadTimestamp?.seconds ?? 0) - (a.uploadTimestamp?.seconds ?? 0)
            );
            
            console.log("Fetched recordings:", sortedRecordings);
            return sortedRecordings; // Return the fetched data
        
        } catch (err) {
            console.error('Error fetching recordings:', err);
            if (err.response && (err.response.status === 401 || err.response.status === 403)) {
                setError("Session expired or not authorized. Please log in again.");
                localStorage.removeItem('AuthToken');
                navigate('/signin');
            } else {
                setError('Failed to fetch recordings. Please try again later.');
            }
            return null; // Return null on error
        }
    }, [navigate]); // Include navigate in dependency array

    const startPolling = useCallback((initialData) => {
        // Clear any existing interval
        if (pollIntervalRef.current) {
            clearInterval(pollIntervalRef.current);
            pollIntervalRef.current = null;
            console.log("Cleared existing poll interval");
        }

        // Check if polling is needed: Poll if ANY recording is in a non-terminal, non-timed-out state.
        const needsPolling = initialData?.some(rec => {
            const statusUpper = rec.status?.toUpperCase();
            const isTerminal = TERMINAL_STATUSES.includes(statusUpper);
            if (isTerminal) return false; // Don't poll for terminal states

            const isUploading = UPLOADING_STATUSES.includes(statusUpper);
            if (isUploading) {
                const elapsedSeconds = rec.uploadTimestamp?.seconds 
                    ? (Date.now() / 1000) - rec.uploadTimestamp.seconds 
                    : 0;
                if (elapsedSeconds > UPLOAD_TIMEOUT_SECONDS) {
                    console.log(`[Polling Check - ${rec.id}] Upload status ${statusUpper} timed out (${Math.round(elapsedSeconds)}s > ${UPLOAD_TIMEOUT_SECONDS}s). No polling needed for this item.`);
                    return false; // Don't poll if upload status has timed out
                }
            }
            return true; // Needs polling (non-terminal and not timed out)
        });

        if (!needsPolling) {
            console.log("No recordings require further status checks (all terminal or timed-out uploads). Polling not started.");
            return;
        }

        console.log("Starting polling for recordings not in a terminal or timed-out upload state...");
        pollIntervalRef.current = setInterval(async () => {
            if (!isMountedRef.current) {
                 console.log("Component unmounted, stopping poll interval.");
                 clearInterval(pollIntervalRef.current);
                 pollIntervalRef.current = null;
                 return;
            }
            console.log("Polling for updates...");
            const newData = await fetchRecordings();
            if (newData && isMountedRef.current) {
                setRecordings(newData);
                // Check if polling should stop: Stop if ALL recordings are terminal OR timed-out uploads.
                const stillNeedsPolling = newData.some(rec => {
                    const statusUpper = rec.status?.toUpperCase();
                    const isTerminal = TERMINAL_STATUSES.includes(statusUpper);
                    if (isTerminal) return false;

                    const isUploading = UPLOADING_STATUSES.includes(statusUpper);
                    if (isUploading) {
                        const elapsedSeconds = rec.uploadTimestamp?.seconds
                            ? (Date.now() / 1000) - rec.uploadTimestamp.seconds
                            : 0;
                        if (elapsedSeconds > UPLOAD_TIMEOUT_SECONDS) {
                            return false; // Timed out upload doesn't need polling
                        }
                    }
                    return true; // Still needs polling
                });

                if (!stillNeedsPolling) {
                    console.log("All recordings are in a terminal state or have timed-out uploads. Stopping polling.");
                    clearInterval(pollIntervalRef.current);
                    pollIntervalRef.current = null;
                } else {
                    console.log("Some recordings still require status checks. Continuing poll.");
                }
            } else if (!newData && isMountedRef.current) {
                 // Handle fetch error during polling - maybe stop polling?
                 console.error("Error fetching data during poll, stopping polling.");
                 clearInterval(pollIntervalRef.current);
                 pollIntervalRef.current = null;
                 // Optionally set an error state related to polling failure
            }
        }, 7000); // Poll every 7 seconds (adjust as needed)

    }, [fetchRecordings]); // Depend on fetchRecordings

    // Initial fetch and setup polling
    useEffect(() => {
         isMountedRef.current = true;
         setLoading(true);
         fetchRecordings().then(initialData => {
             if (initialData && isMountedRef.current) {
                 setRecordings(initialData);
                 startPolling(initialData); // Start polling only if initial fetch succeeded
             }
             if (isMountedRef.current) { // Check mount status before setting loading state
                 setLoading(false);
             }
         });

         // Cleanup function
         return () => {
             console.log("RecordingList component unmounting, clearing interval.");
             isMountedRef.current = false; // Set mount status to false
             if (pollIntervalRef.current) {
                 clearInterval(pollIntervalRef.current);
                 pollIntervalRef.current = null;
             }
         };
    }, [fetchRecordings, startPolling]); // Dependencies for initial setup

    const handleDelete = async (idToDelete, nhostFileId) => {
        if (!window.confirm('Are you sure you want to delete this recording and its summary? This action cannot be undone.')) {
            return;
        }

        const token = localStorage.getItem('AuthToken');
        if (!token) {
            setError("Authentication required.");
            navigate('/signin');
            return;
        }

        try {
            // Optional: Delete from Nhost first (consider atomicity)
            // if (nhostFileId) { ... Nhost delete logic ... }

            // Delete metadata and trigger backend cleanup
            const url = `${API_BASE_URL}api/audio/metadata/${idToDelete}`;
            const response = await axios.delete(url, {
                headers: { 'Authorization': `Bearer ${token}` }
            });

            if (response.status === 200 || response.status === 204) {
                setRecordings(prev => prev.filter(rec => rec.id !== idToDelete));
                setError(null); // Clear any previous errors
                console.log(`Recording ${idToDelete} deleted successfully.`);
            } else {
                 throw new Error(`Failed to delete. Status: ${response.status}`);
            }
        } catch (err) {
            console.error('Error deleting recording:', err);
             if (err.response && (err.response.status === 401 || err.response.status === 403)) {
                 setError("Session expired or not authorized. Please log in again.");
                 localStorage.removeItem('AuthToken');
                 navigate('/signin');
             } else {
                 setError('Failed to delete recording. Please try again.');
             }
        }
    };

    const formatDate = (timestamp) => {
        if (!timestamp?.seconds) return 'N/A';
        return new Date(timestamp.seconds * 1000).toLocaleDateString();
    };

    const formatDuration = (seconds) => {
        if (seconds === null || typeof seconds !== 'number') return 'N/A';
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = Math.round(seconds % 60);
        return `${minutes}m ${remainingSeconds}s`;
    };

    const getStatusBadge = (recording) => {
        const { status, failureReason, uploadTimestamp } = recording;
        const originalStatus = status;
        const statusUpper = status?.toUpperCase() ?? 'UNKNOWN';
        let bgColor = 'bg-gray-100';
        let textColor = 'text-gray-800';
        let Icon = FiClock;
        let displayStatus = 'Unknown';
        let isSpinning = false;
        let titleText = '';

        const isUploading = UPLOADING_STATUSES.includes(statusUpper);
        const elapsedSeconds = uploadTimestamp?.seconds 
            ? (Date.now() / 1000) - uploadTimestamp.seconds 
            : 0;
        const isTimedOutUpload = isUploading && elapsedSeconds > UPLOAD_TIMEOUT_SECONDS;

        if (isTimedOutUpload) {
            bgColor = 'bg-gray-100';
            textColor = 'text-gray-700';
            Icon = FiClock; // Static clock
            displayStatus = 'Processing Upload';
            isSpinning = false;
            titleText = `Upload received ${Math.round(elapsedSeconds / 60)} mins ago, processing initiated.`;
            console.log(`[Badge Timeout - ${recording.id}] Displaying timed-out upload status.`);
        } else {
             // Normal status handling
            switch (statusUpper) {
                case 'COMPLETED':
                case 'PROCESSED': // Consider PROCESSED as COMPLETED for display
                    bgColor = 'bg-green-100';
                    textColor = 'text-green-800';
                    Icon = FiCheckCircle;
                    displayStatus = 'Completed';
                    break;
                case 'UPLOADING_TO_STORAGE':
                case 'UPLOAD_IN_PROGRESS':
                    bgColor = 'bg-blue-100';
                    textColor = 'text-blue-800';
                    Icon = FiUploadCloud;
                    displayStatus = 'Uploading';
                    isSpinning = true;
                    break;
                case 'PROCESSING':
                case 'PROCESSING_QUEUED': // Added queued state
                case 'TRANSCRIBING':
                case 'SUMMARIZING':
                case 'PDF_CONVERTING':
                    bgColor = 'bg-yellow-100';
                    textColor = 'text-yellow-800';
                    Icon = FiLoader;
                    displayStatus = 'Processing';
                    isSpinning = true;
                    break;
                case 'FAILED':
                case 'PROCESSING_HALTED_UNSUITABLE_CONTENT':
                case 'PROCESSING_HALTED_NO_SPEECH':
                    bgColor = 'bg-red-100';
                    textColor = 'text-red-800';
                    Icon = FiAlertTriangle;
                    displayStatus = 'Failed';
                    break;
                 // Add cases for transcription/pdf completion if needed, or treat as Processing
                 case 'TRANSCRIPTION_COMPLETE':
                 case 'PDF_CONVERSION_COMPLETE':
                     bgColor = 'bg-yellow-100';
                     textColor = 'text-yellow-800';
                     Icon = FiLoader; // Still processing overall
                     displayStatus = 'Processing';
                     isSpinning = true;
                     break;
                default:
                    displayStatus = 'Unknown';
                    if (statusUpper !== 'UNKNOWN') {
                         console.warn('[Badge] Unknown recording status received:', originalStatus);
                     }
                    break;
            }
            titleText = (displayStatus === 'Failed' && failureReason)
                        ? `${displayStatus}: ${failureReason}`
                        : displayStatus;
        }

        return (
            <span
                title={titleText}
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${bgColor} ${textColor}`}
            >
                 <Icon className={`mr-1 h-3 w-3 ${isSpinning ? 'animate-spin' : ''}`} />
                {displayStatus}
            </span>
        );
    };

    return (
        <>
            <Header />
            <main className="flex-grow py-12 bg-gray-50">
                 <title>AudioScholar - My Recordings</title>
                <div className="container mx-auto px-4">
                    <h1 className="text-3xl font-bold text-gray-800 mb-8">My Recordings</h1>

                    {loading && (
                        <div className="text-center py-10">
                            <FiLoader className="animate-spin h-8 w-8 mx-auto text-teal-600" />
                            <p className="mt-2 text-gray-600">Loading recordings...</p>
                        </div>
                    )}

                    {error && (
                        <div className="mb-6 bg-red-50 text-red-700 p-4 rounded-lg text-center">
                            <p>{error}</p>
                        </div>
                    )}

                    {!loading && recordings.length === 0 && !error && (
                        <div className="text-center py-10 bg-white rounded-lg shadow p-6">
                            <FiFile className="mx-auto h-12 w-12 text-gray-400 mb-4"/>
                            <p className="text-gray-600">You haven't uploaded any recordings yet.</p>
                            <Link to="/upload" className="mt-4 inline-block bg-[#2D8A8A] hover:bg-[#236b6b] text-white font-medium py-2 px-5 rounded-md transition">
                                Upload Your First Recording
                            </Link>
                        </div>
                    )}

                    {!loading && recordings.length > 0 && (
                        <div className="bg-white rounded-lg shadow overflow-hidden">
                            <ul className="divide-y divide-gray-200">
                                {recordings.map((recording) => (
                                    <li key={recording.id} className="px-6 py-4 hover:bg-gray-50 transition duration-150 ease-in-out">
                                        <div className="flex items-center justify-between flex-wrap gap-4">
                                            <div className="flex-1 min-w-0">
                                                <Link to={`/recordings/${recording.id}`} className="text-lg font-semibold text-teal-700 hover:text-teal-900 truncate block" title={recording.title}>
                                                    {recording.title || 'Untitled Recording'}
                                                </Link>
                                                <p className="text-sm text-gray-500 mt-1">Uploaded: {formatDate(recording.uploadTimestamp)}</p>
                                            </div>
                                            <div className="flex items-center space-x-4 flex-shrink-0">
                                                 {/* <span className="text-sm text-gray-500">{formatDuration(recording.durationSeconds)}</span> */} 
                                                 {getStatusBadge(recording)}
                                                <Link to={`/recordings/${recording.id}`} className="text-sm text-indigo-600 hover:text-indigo-800 font-medium inline-flex items-center" title="View Details">
                                                     View Details <FiExternalLink className="ml-1 h-3 w-3"/>
                                                 </Link>
                                                <button
                                                    onClick={() => handleDelete(recording.id, recording.nhostFileId)}
                                                    className="text-red-500 hover:text-red-700 p-1 rounded-md hover:bg-red-100 transition"
                                                    title="Delete Recording"
                                                >
                                                    <FiTrash2 className="h-4 w-4" />
                                                </button>
                                            </div>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>
            </main>
        </>
    );
};

export default RecordingList;