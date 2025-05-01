import axios from 'axios';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FiAlertTriangle, FiCheckCircle, FiClock, FiExternalLink, FiFile, FiLoader, FiTrash2, FiUploadCloud } from 'react-icons/fi';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Footer, Header } from '../Home/HomePage';

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

        // Check if polling is needed (Processing OR Uploading)
        const needsPolling = initialData?.some(rec => 
            rec.status === 'PROCESSING' || rec.status === 'UPLOADING_TO_STORAGE'
        );
        if (!needsPolling) {
            console.log("No recordings are processing or uploading. Polling not started.");
            return; 
        }

        console.log("Starting polling for active recordings...");
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
                // Check if polling should stop (No Processing AND No Uploading)
                const stillActive = newData.some(rec => 
                    rec.status === 'PROCESSING' || rec.status === 'UPLOADING_TO_STORAGE'
                );
                if (!stillActive) {
                    console.log("All recordings processed/uploaded. Stopping polling.");
                    clearInterval(pollIntervalRef.current);
                    pollIntervalRef.current = null;
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

    const getStatusBadge = (status, failureReason) => {
        const originalStatus = status; // Keep original value for logging
        status = status?.toLowerCase() ?? 'unknown'; // Default to 'unknown' if null/undefined
        let bgColor = 'bg-gray-100';
        let textColor = 'text-gray-800';
        let Icon = FiClock;
        let displayStatus = 'Unknown'; // Default display text

        switch (status) {
            case 'completed':
            case 'processed':
                bgColor = 'bg-green-100';
                textColor = 'text-green-800';
                Icon = FiCheckCircle;
                displayStatus = 'Completed';
                break;
            case 'uploading_to_storage': // Handle new status
                bgColor = 'bg-blue-100';
                textColor = 'text-blue-800';
                Icon = FiUploadCloud; // Use upload icon
                displayStatus = 'Uploading';
                break;
            case 'processing':
                bgColor = 'bg-yellow-100';
                textColor = 'text-yellow-800';
                Icon = FiLoader;
                displayStatus = 'Processing';
                break;
            case 'failed':
            case 'processing_halted_unsuitable_content':
                bgColor = 'bg-red-100';
                textColor = 'text-red-800';
                Icon = FiAlertTriangle;
                displayStatus = 'Failed';
                break;
            // Add more cases here if other statuses are expected (e.g., 'pending', 'queued')
            // case 'pending':
            //     bgColor = 'bg-blue-100';
            //     textColor = 'text-blue-800';
            //     Icon = FiClock;
            //     displayStatus = 'Pending';
            //     break;
            default:
                // Keep default gray style for unknown, but log the actual status received
                if (status !== 'unknown') { // Only log if it wasn't originally null/undefined
                     console.warn('Unknown recording status received:', originalStatus);
                 }
                // Use FiClock or maybe FiHelpCircle for Unknown
                break;
        }

        return (
            <span
                title={displayStatus === 'Failed' ? failureReason || 'Processing failed' : displayStatus}
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${bgColor} ${textColor}`}
            >
                 <Icon className={`mr-1 h-3 w-3 ${displayStatus === 'Processing' || displayStatus === 'Uploading' ? 'animate-spin' : ''}`} />
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
                                                 {getStatusBadge(recording.status, recording.failureReason)}
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
            <Footer />
        </>
    );
};

export default RecordingList;